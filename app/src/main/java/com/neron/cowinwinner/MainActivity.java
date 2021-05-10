package com.neron.cowinwinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener, OTPDelegate {

    private CoWinClient coWinClient;
    private SmsReceiver smsReceiver;
    private String txnId;
    private String authToken;
    private boolean processingSlots;
    private boolean verified;
    private Button otpButton;
    private Switch autoVerifySwitch;
    private int retries = 0;
    private ArrayList<String> beneficiariesRefId = new ArrayList<>();
    private TextView minAgeTextView;
    private WebView captchaWebView;

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.coWinClient = new CoWinClient();
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS}, PackageManager.PERMISSION_GRANTED);
        smsReceiver = new SmsReceiver();
        smsReceiver.otpDelegate = this;
        Switch aSwitch = findViewById(R.id.switch1);
        aSwitch.setOnCheckedChangeListener(this);
        EditText pincodeEt = findViewById(R.id.pincode);
        pincodeEt.setText("302020");
        EditText dateEt = findViewById(R.id.date);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        dateEt.setText(LocalDate.now().format(formatter));
        dateEt.setText("10-05-2021");
        otpButton = findViewById(R.id.otpButton);
        autoVerifySwitch = findViewById(R.id.autoVerifySwitch);
        String token = this.readTokenFromFile(this.getBaseContext());
        if (token != null) {
            onVerification(token);
        }
        minAgeTextView = findViewById(R.id.minAge);
        captchaWebView = findViewById(R.id.svgView);
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

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            MainActivity self = this;
            Runnable r = () -> {
                Switch aSwitch = findViewById(R.id.switch1);
                while (aSwitch.isChecked()) {
                    EditText pincodeEt = findViewById(R.id.pincode);
                    EditText dateEt = findViewById(R.id.date);
                    self.processingSlots = true;
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
                                            if (session.getInt("min_age_limit") == minAge && session.getInt("available_capacity") > 0) {
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
                    if (verified) {
                        self.coWinClient.getSlots(pincodeEt.getText().toString(), dateEt.getText().toString(), self.authToken, callback);
                    } else {
                        self.coWinClient.getSlots(pincodeEt.getText().toString(), dateEt.getText().toString(), callback);
                    }
                    while (self.processingSlots) {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
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
                if (response.code() == 401) {
                    self.verificationFailed();
                }
                if (response.isSuccessful()) {
                    JSONObject jsonObject = null;
                    try {
                        jsonObject = new JSONObject(response.body().string());
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

    public void fetchBeneficiary(View view) {
        this.beneficiariesRefId = new ArrayList<>();
        this.coWinClient.getBeneficiaries(this.authToken, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.code() == 401) {
                    verificationFailed();
                }
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        String beneficiariesString = "Beneficiaries: \n";
                        JSONArray beneficiaries = jsonObject.getJSONArray("beneficiaries");
                        for (int i = 0; i < beneficiaries.length(); i++) {
                            JSONObject beneficiary = beneficiaries.getJSONObject(i);
                            if (!beneficiariesRefId.contains(beneficiary.getString("beneficiary_reference_id")) && beneficiary.getString("vaccination_status").equals("Not Vaccinated")) {
                                beneficiariesRefId.add(beneficiary.getString("beneficiary_reference_id"));
                                beneficiariesString = beneficiariesString + (i+1) + ". " + beneficiary.getString("name");
                            }
                        }
                        String finalBeneficiariesString = beneficiariesString;
                        runOnUiThread(() -> {
                            TextView beneficiariesView = findViewById(R.id.beneficiaries);
                            beneficiariesView.setText(finalBeneficiariesString);
                        });
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        fetchCaptcha(view);
    }

    public void fetchCaptcha(View view) {
        MainActivity self = this;
        this.coWinClient.getCaptcha(this.authToken, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.code() == 401) {
                    self.verificationFailed();
                }
                if (response.isSuccessful()) {
                    JSONObject jsonObject = null;
                    try {
                        jsonObject = new JSONObject(response.body().string());
                        String svgString = jsonObject.getString("captcha");
                        runOnUiThread(() -> {
                            captchaWebView.loadDataWithBaseURL("file://android_assest", svgString, "text/html", "UTF-8", null);
                            captchaWebView.setWebViewClient(new WebViewClient() {
                                @Override
                                public void onPageCommitVisible (WebView view, String url) {
                                    try {
                                        Thread.sleep(5000);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    solveCaptcha();
                                }
                            });
                        });
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void onVerification(String token) {
        verified = true;
        this.authToken = token;
        this.writeTokenToFile(token, this.getBaseContext());
        setStatus("Verified 🙂");
        runOnUiThread(() -> {
            otpButton.setText("Verified ✔️");
            otpButton.setEnabled(false);
        });
        MainActivity self = this;
        Runnable r = () -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            self.fetchBeneficiary(null);
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
            this.coWinClient.bookSlot(1, captcha, centerId, sessionId, slot, beneficiariesRefId, this.authToken, new Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    setStatus("Failed to book slot\n" +
                            e.toString());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.code() == 401) {
                        verificationFailed();
                    }
                    if (response.isSuccessful()) {
                        setStatus("Slot Booked");
                    } else {
                        setStatus("Failed to book slot\n" +
                                response.body().string());
                    }
                }
            });
        }
    }

    private void writeTokenToFile(String data, Context context) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput("auth.txt", Context.MODE_PRIVATE));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String readTokenFromFile(Context context) {
        String ret = "";
        try {
            InputStream inputStream = context.openFileInput("auth.txt");

            if (inputStream != null) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                StringBuilder stringBuilder = new StringBuilder();

                while ((receiveString = bufferedReader.readLine()) != null) {
                    stringBuilder.append(receiveString);
                }

                inputStream.close();
                ret = stringBuilder.toString();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ret;
    }
}