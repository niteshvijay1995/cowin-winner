package com.neron.cowinwinner;

import android.content.Context;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class UserAuth implements Serializable {
    String phoneNumber;
    String token;

    public void writeToFile(Context context) {
        try {
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(context.openFileOutput("auth.txt", Context.MODE_PRIVATE));
            objectOutputStream.writeObject(this);
            objectOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static UserAuth readFromFile(Context context) throws IOException, ClassNotFoundException {
        UserAuth userAuth = new UserAuth();
        InputStream inputStream = context.openFileInput("auth.txt");
        if (inputStream != null) {
            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
            userAuth = (UserAuth) objectInputStream.readObject();
            objectInputStream.close();
        }
        return userAuth;
    }
}
