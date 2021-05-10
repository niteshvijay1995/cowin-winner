package com.neron.cowinwinner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class CoWinClient {

    OkHttpClient httpClient;

    public CoWinClient() {
        this.httpClient = new OkHttpClient().newBuilder()
                .build();
    }

    public void generateOTP(String mobileNumber, Callback callback) throws JSONException {
        MediaType mediaType = MediaType.parse("application/json");
        JSONObject jsonBody = new JSONObject();
        jsonBody.put("mobile", mobileNumber);
        jsonBody.put("secret", "U2FsdGVkX1/y/HlZKT1VvafAy66+CEbgKYj3wmjKaHcZwrX/bO59AKMoAIiQbP59G8bEyCch6OK/WhP9y3xgug==");
        RequestBody body = RequestBody.create(mediaType, jsonBody.toString());
        Request request = new Request.Builder()
                .url("https://cdn-api.co-vin.in/api/v2/auth/generateMobileOTP")
                .method("POST", body)
                .addHeader("accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36")
                .build();
        Call call = this.httpClient.newCall(request);
        call.enqueue(callback);
    }

    public static String sha256(final String base) {
        try{
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(base.getBytes("UTF-8"));
            final StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < hash.length; i++) {
                final String hex = Integer.toHexString(0xff & hash[i]);
                if(hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch(Exception ex){
            throw new RuntimeException(ex);
        }
    }

    public void verifyOtp(String otp, String txnId, Callback callback) {
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, "{\"otp\":\"" + sha256(otp) + "\",\"txnId\":\"" + txnId + "\"}");
        Request request = new Request.Builder()
                .url("https://cdn-api.co-vin.in/api/v2/auth/validateMobileOtp")
                .method("POST", body)
                .addHeader("accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36")
                .build();
        Call call = this.httpClient.newCall(request);
        call.enqueue(callback);
    }

    public Call getSlots(String pincode, String date, String token, Callback callback) {
        Request request = new Request.Builder()
                .url("https://cdn-api.co-vin.in/api/v2/appointment/sessions/calendarByPin?pincode=" + pincode + "&date=" + date)
                .method("GET", null)
                .addHeader("accept", "application/json")
                .addHeader("Accept-Language", "hi_IN")
                .addHeader("authorization", "Bearer " + token)
                .addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36")
                .build();
        Call call = this.httpClient.newCall(request);
        call.enqueue(callback);
        return call;
    }

    public Call getBeneficiaries(String token, Callback callback) {
        this.httpClient = new OkHttpClient().newBuilder()
                .build();
        Request request = new Request.Builder()
                .url("https://cdn-api.co-vin.in/api/v2/appointment/beneficiaries")
                .method("GET", null)
                .addHeader("accept", "application/json")
                .addHeader("authorization", "Bearer " + token)
                .addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36")
                .build();
        Call call = this.httpClient.newCall(request);
        call.enqueue(callback);
        return call;
    }

    public Call bookSlot(int dose, String captcha, String centerId, String sessionId, String slot, List<String> beneficiaries, String authToken, Callback callback) throws JSONException {
        MediaType mediaType = MediaType.parse("application/json");
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("dose", dose);
        jsonObject.put("session_id", sessionId);
        jsonObject.put("slot", slot);
        jsonObject.put("beneficiaries", new JSONArray(beneficiaries));
        jsonObject.put("center_id", centerId);
        jsonObject.put("captcha", captcha);
        RequestBody body = RequestBody.create(mediaType, jsonObject.toString());
        Request request = new Request.Builder()
                .url("https://cdn-api.co-vin.in/api/v2/appointment/schedule")
                .method("POST", body)
                .addHeader("accept", "application/json")
                .addHeader("authorization", "Bearer " + authToken)
                .addHeader("Content-Type", "application/json")
                .addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36")
                .build();
        Call call = this.httpClient.newCall(request);
        call.enqueue(callback);
        return call;
    }

    public void getCaptcha(String authToken, Callback callback) {
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, "{}");
        Request request = new Request.Builder()
                .url("https://cdn-api.co-vin.in/api/v2/auth/getRecaptcha")
                .method("POST", body)
                .addHeader("accept", "application/json")
                .addHeader("authorization", "Bearer " + authToken)
                .addHeader("Content-Type", "application/json")
                .addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36")
                .build();
        Call call = this.httpClient.newCall(request);
        call.enqueue(callback);
    }

    public Call validateCaptcha(String authToken, String captcha, Callback callback) throws JSONException {
        MediaType mediaType = MediaType.parse("application/json");
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("dose", 1);
        jsonObject.put("session_id", "2b8bdf32-14a5-4840-a936-70337563acca");
        jsonObject.put("slot", "11:00AM-01:00PM");
        jsonObject.put("beneficiaries", new JSONArray("[\"73736258463880\"]"));
        jsonObject.put("center_id", 411587);
        jsonObject.put("captcha", captcha);
        RequestBody body = RequestBody.create(mediaType, jsonObject.toString());
        Request request = new Request.Builder()
                .url("https://cdn-api.co-vin.in/api/v2/appointment/schedule")
                .method("POST", body)
                .addHeader("accept", "application/json")
                .addHeader("authorization", "Bearer " + authToken)
                .addHeader("Content-Type", "application/json")
                .addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36")
                .build();
        Call call = this.httpClient.newCall(request);
        call.enqueue(callback);
        return call;

    }
}
