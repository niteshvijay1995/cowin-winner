package com.neron.cowinwinner;

import com.google.android.material.chip.Chip;

public class Beneficiary {
    String name;
    String id;
    String vaccinationStatus;
    Chip chip;

    public Beneficiary(String name, String id, String vaccinationStatus) {
        this.name = name;
        this.id = id;
        this.vaccinationStatus = vaccinationStatus;
    }
}
