package com.neron.cowinwinner;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener, OTPDelegate {

    private final List<String> CAPTCHA_FAILURE_ERROR_CODES = Arrays.asList(new String[]{"APPOIN0044", "APPOIN0045"});

    private CoWinClient coWinClient;
    private SmsReceiver smsReceiver;
    private String txnId;
    private boolean processingSlots;
    private boolean verified;
    private Button otpButton;
    private Switch autoVerifySwitch;
    private int retries = 0;
    private ArrayList<Beneficiary> beneficiariesRefId = new ArrayList<>();
    private TextView minAgeTextView;
    private WebView captchaWebView;
    private TextView captchaTextView;
    private Vibrator vib;
    private Ringtone ringtone;
    private TextView captchaStatus;
    private EditText phoneNumberEditText;
    private Switch aSwitch;
    ChipGroup beneficiaryChipGroup;
    HashSet<String> bridsSet = new HashSet<>();
    UserAuth userAuth;

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getSupportActionBar().hide();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.coWinClient = new CoWinClient();
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS}, PackageManager.PERMISSION_GRANTED);
        smsReceiver = new SmsReceiver();
        smsReceiver.otpDelegate = this;
        aSwitch = findViewById(R.id.switch1);
        aSwitch.setOnCheckedChangeListener(this);
        EditText pincodeEt = findViewById(R.id.pincode);
        pincodeEt.setText("302020");
        EditText dateEt = findViewById(R.id.date);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        dateEt.setText(LocalDate.now().plusDays(1).format(formatter));
        otpButton = findViewById(R.id.otpButton);
        autoVerifySwitch = findViewById(R.id.autoVerifySwitch);
        minAgeTextView = findViewById(R.id.minAge);
        captchaWebView = findViewById(R.id.svgView);
        captchaStatus = findViewById(R.id.captchaStatus);
        beneficiaryChipGroup = findViewById(R.id.beneficiaryChips);
        captchaTextView = findViewById(R.id.captchatext);
        phoneNumberEditText = findViewById(R.id.phoneNumberInput);
        autoVerifySwitch.setChecked(true);
        loadUserAuthFromPersistenceStore();
    }

    private void loadUserAuthFromPersistenceStore() {
        this.userAuth = new UserAuth();
        try {
            this.userAuth = UserAuth.readFromFile(this.getBaseContext());
            phoneNumberEditText.setText(userAuth.phoneNumber);
            MainActivity self = this;
            Runnable r = () -> {
                self.fetchBeneficiary(null);
                self.fetchCaptcha(null);
            };
            new Thread(r).start();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void setStatus(String status) {
        runOnUiThread(() -> {
            TextView statusView = findViewById(R.id.statusView);
            statusView.setText(status);
        });
    }

    public void sendOtp(View view) throws JSONException {
        EditText et = findViewById(R.id.phoneNumberInput);
        String mobileNumber = et.getText().toString();
        userAuth = new UserAuth();
        userAuth.phoneNumber = mobileNumber;
        if (mobileNumber.length() != 10) {
            setStatus("Phone number is not valid!!");
            return;
        }
        MainActivity self = this;
        Button button = (Button)view;
        button.setEnabled(false);
        this.coWinClient.generateOTP(mobileNumber, new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> button.setEnabled(true));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    JSONObject jsonObject = null;
                    try {
                        jsonObject = new JSONObject(response.body().string());
                        self.setTxnId((String) jsonObject.get("txnId"));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    setStatus("OTP triggered, Reading OTP from SMS...");
                    runOnUiThread(() -> {
                        button.setEnabled(false);
                        new CountDownTimer(10000, 1000) {

                            @Override
                            public void onTick(long millisUntilFinished) {
                                if (verified) {
                                    this.cancel();
                                }
                                button.setText("Wait for " + millisUntilFinished / 1000);
                            }

                            @Override
                            public void onFinish() {
                                if (!verified) {
                                    button.setText("Verify again!");
                                    button.setEnabled(true);
                                }
                            }
                        }.start();
                    });
                } else {
                    setStatus("Error while sending OTP: \n" +
                            response.body().string());
                    runOnUiThread(() -> {
                        button.setEnabled(true);
                    });
                }
            }
        });
    }

    public void checkSlots() {
        EditText pincodeEt = findViewById(R.id.pincode);
        EditText dateEt = findViewById(R.id.date);
        this.processingSlots = true;
        MainActivity self = this;
        Callback callback = new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                self.processingSlots = false;
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.code() == 401) {
                    self.verificationFailed();
                }
                if (response.isSuccessful()) {
                    JSONObject jsonObject;
                    try {
                        jsonObject = new JSONObject(response.body().string());
                        boolean slotFound = false;
                        JSONArray centers = (JSONArray)jsonObject.get("centers");
                        String centerString = "";
                        for (int i = 0; i < centers.length(); i++) {
                            JSONObject center = centers.getJSONObject(i);
                            JSONArray sessions = center.getJSONArray("sessions");
                            for (int j = 0; j <sessions.length(); j++) {
                                JSONObject session = sessions.getJSONObject(j);
                                int minAge;
                                try {
                                    minAge = Integer.parseInt(minAgeTextView.getText().toString());
                                } catch (Exception ex) {
                                    minAge = 18;
                                }
                                if (session.getInt("min_age_limit") >= minAge && session.getInt("available_capacity") > 0) {
                                    slotFound = true;
                                    self.onSlotFound(center.getString("center_id"), session);
                                }
                            }
                            centerString += (i+1) + ". " + center.getString("name") + "\n";
                        }
                        if (!slotFound) {
                            setStatus("No slot found\n" +
                                    "Last checked at " + LocalDateTime.now() + "\n" +
                                    "Centers checked : \n" + centerString);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                self.processingSlots = false;
            }
        };
        self.coWinClient.getSlots(pincodeEt.getText().toString(), dateEt.getText().toString(), userAuth.token, callback);
        while (self.processingSlots) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            Runnable r = () -> {
                Switch aSwitch = findViewById(R.id.switch1);
                int counter = 0;
                while (aSwitch.isChecked()) {
                    counter++;
                    checkSlots();
                    if (counter % 5 == 0) {
                        validateCaptcha();
                        fetchBeneficiary(null);
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            };
            new Thread(r).start();
        }
    }

    @Override
    public void onOTPReceived(String otp) {
        setStatus("Received OTP " + otp + "\n" +
                "Verifying...");
        MainActivity self = this;
        this.coWinClient.verifyOtp(otp, this.txnId, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                setStatus("Failed to verify otp\n" +
                        e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                self.validateResponse(response);
                if (response.isSuccessful()) {
                    JSONObject jsonObject;
                    try {
                        jsonObject = new JSONObject(response.body().string());
                        setStatus("Verified ✅");
                        self.onVerification((jsonObject.getString("token")));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    setStatus("Failed to verify otp\n" +
                            response.body().string());
                }
            }
        });
    }

    private void validateCaptcha() {
        String captcha = captchaTextView.getText().toString();
        try {
            this.coWinClient.validateCaptcha(userAuth.token, captcha, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                }

                @RequiresApi(api = Build.VERSION_CODES.Q)
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    validateResponse(response);
                    try {
                        JSONObject responseObject = new JSONObject(response.body().string());
                        if (CAPTCHA_FAILURE_ERROR_CODES.contains(responseObject.getString("errorCode"))) {
                            setCaptchaStatus(false);
                            refreshCaptcha();
                        } else {
                            setStatus("Captcha Validated at " + LocalDateTime.now());
                            setCaptchaStatus(true);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (JSONException e) {
            setStatus("Error while validating captcha" +
                    e.getMessage());
        }
    }

    public void fetchBeneficiary(View view) {
        this.coWinClient.getBeneficiaries(userAuth.token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                validateResponse(response);
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        String beneficiariesString = "Beneficiaries: \n";
                        JSONArray beneficiaries = jsonObject.getJSONArray("beneficiaries");
                        for (int i = 0; i < beneficiaries.length(); i++) {
                            JSONObject beneficiary = beneficiaries.getJSONObject(i);
                            String brid = beneficiary.getString("beneficiary_reference_id");
                            if (!bridsSet.contains(brid)) {
                                beneficiariesString = beneficiariesString + (i+1) + ". " + beneficiary.getString("name");
                                beneficiariesRefId.add(new Beneficiary(beneficiary.getString("name"),
                                        beneficiary.getString("birth_year"),
                                        beneficiary.getString("beneficiary_reference_id"),
                                        beneficiary.getString("vaccination_status")));
                                bridsSet.add(brid);
                            }
                        }
                        runOnUiThread(() -> {
                            otpButton.setText("Verified ✅️");
                            otpButton.setEnabled(false);
                            for (Beneficiary b : beneficiariesRefId) {
                                if (b.chip == null) {
                                    Chip c = new Chip(beneficiaryChipGroup.getContext());
                                    c.setText(b.name + " (" + b.age + ")");
                                    c.setCloseIconVisible(false);
                                    c.setCheckable(true);
                                    c.setChecked(true);
                                    beneficiaryChipGroup.addView(c);
                                    b.chip = c;
                                }
                            }
                        });
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public void fetchCaptcha(View view) {
        MainActivity self = this;
        this.coWinClient.getCaptcha(userAuth.token, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                validateResponse(response);
                if (response.isSuccessful()) {
                    JSONObject jsonObject = null;
                    try {
                        jsonObject = new JSONObject(response.body().string());
                        String svgString = jsonObject.getString("captcha");
                        runOnUiThread(() -> {
                            captchaWebView.loadDataWithBaseURL("file://android_assest", svgString, "text/html", "UTF-8", null);
                            captchaTextView.requestFocus();
                        });
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    if (ringtone != null) ringtone.stop();
                    if (vib != null) vib.cancel();
                }
            }
        });
    }

    private void onVerification(String token) {
        verified = true;
        userAuth.token = token;
        userAuth.writeToFile(this.getBaseContext());
        MainActivity self = this;
        Runnable r = () -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            self.fetchBeneficiary(null);
            self.fetchCaptcha(null);
        };
        new Thread(r).start();
    }

    private void verificationFailed() {
        setStatus("Verification failed...");
        retries++;
        if (retries > 5) {
            retries = 0;
            verified = false;
            runOnUiThread(() -> {
                this.otpButton.setEnabled(true);
                this.otpButton.setText("Verify again");
                if (autoVerifySwitch.isChecked()) {
                    try {
                        this.sendOtp(otpButton);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    Switch aSwitch = findViewById(R.id.switch1);
                    aSwitch.setChecked(false);
                }
            });
        }
    }

    private void onSlotFound(String centerId, JSONObject session) throws JSONException {
        setStatus("Found slot... Booking now...");
        String sessionId = session.getString("session_id");
        JSONArray slots = session.getJSONArray("slots");
        for (int k = 0; k < slots.length(); k++) {
            String slot = slots.getString(k);
            String captcha = ((TextView)findViewById(R.id.captchatext)).getText().toString();
            List<String> bList = beneficiariesRefId.stream().filter(b -> b.chip.isChecked()).map(b -> b.id).collect(Collectors.toList());
            this.coWinClient.bookSlot(1, captcha, centerId, sessionId, slot, bList, userAuth.token, new Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    setStatus("Failed to book slot\n" +
                            e.toString());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    validateResponse(response);
                    if (response.isSuccessful()) {
                        setStatus("Slot Booked");
                        aSwitch.setChecked(false);
                    } else {
                        setStatus("Failed to book slot\n" +
                                response.body().string());
                    }
                }
            });
        }
    }

    private void setCaptchaStatus(boolean status) {
        runOnUiThread(() -> {
            captchaStatus.setText(status ? "✅" : "❌");
            if (!status) {
                Switch aSwitch = findViewById(R.id.switch1);
                aSwitch.setChecked(false);
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void refreshCaptcha() {
        if (ringtone == null || !ringtone.isPlaying()) {
            ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getActualDefaultRingtoneUri(getApplicationContext(), RingtoneManager.TYPE_RINGTONE));
            vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            long[] pattern = {1500, 800, 800, 800};
            vib.vibrate(VibrationEffect.createWaveform(pattern, 0));
            ringtone.play();
        }
        this.fetchCaptcha(null);
    }

    private void validateResponse(Response response) {
        if (response.code() == 401) {
            verificationFailed();
        }
    }
}