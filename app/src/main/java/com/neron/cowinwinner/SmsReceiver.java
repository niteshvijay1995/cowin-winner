package com.neron.cowinwinner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsReceiver extends BroadcastReceiver {

    private final String OriginatingAddress = "NHPSMS";

    public static OTPDelegate otpDelegate;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            for (SmsMessage smsMessage : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                if (smsMessage.getDisplayOriginatingAddress().contains(OriginatingAddress)) {
                    String messageBody = smsMessage.getMessageBody();
                    String otp = getOtpFromMessage(messageBody);
                    if (otp != null && otpDelegate != null) {
                        otpDelegate.onOTPReceived(otp);
                    }
                }
            }
        }
    }

    private String getOtpFromMessage(String message) {
        // This will match any 6 digit number in the message
        Pattern pattern = Pattern.compile("(|^)\\d{6}");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }
}
