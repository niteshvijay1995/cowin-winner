package com.neron.cowinwinner;

import com.google.android.material.chip.Chip;

import java.time.LocalDate;

public class Beneficiary {
    String name;
    int age;
    String id;
    String vaccinationStatus;
    Chip chip;

    public Beneficiary(String name, String birthYear, String id, String vaccinationStatus) {
        this.name = name;
        this.age = LocalDate.now().getYear() - Integer.parseInt(birthYear);
        this.id = id;
        this.vaccinationStatus = vaccinationStatus;
    }
}
