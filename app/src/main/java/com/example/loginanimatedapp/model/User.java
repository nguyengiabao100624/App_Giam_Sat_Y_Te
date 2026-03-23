package com.example.loginanimatedapp.model;

public class User {
    public String fullName;
    public String email;
    public String phone;
    public String gender;
    public String avatarUrl;
    public String dob; // Date of Birth

    // Required empty constructor for Firebase
    public User() {}

    public User(String fullName, String email, String phone, String gender, String avatarUrl) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.gender = gender;
        this.avatarUrl = avatarUrl;
    }

    // Constructor with DOB
    public User(String fullName, String email, String phone, String gender, String avatarUrl, String dob) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.gender = gender;
        this.avatarUrl = avatarUrl;
        this.dob = dob;
    }
}
