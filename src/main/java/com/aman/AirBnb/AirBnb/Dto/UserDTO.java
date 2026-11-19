package com.aman.AirBnb.AirBnb.Dto;

import com.aman.AirBnb.AirBnb.Enums.Gender;
import com.aman.AirBnb.AirBnb.Enums.Role;
import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

@Data
public class UserDTO {
    private Long id;
    private String email;
    private String name;
    private Gender gender;
    private LocalDate dateOfBirth;
    private Set<Role> roles;  // ✅ ADDED
}