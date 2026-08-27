package com.example.demo.model;

import com.example.demo.ai.annotation.AiNotQueryable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.math.BigDecimal;

@Entity
@Table(name = "employee")
public class Employee {

    private Long id;
    private int age;
    private String name;
    private String department;
    private String phone;
    private BigDecimal salary;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", nullable = false)
    public Long getId() {
        return id;
    }

    @Column(name = "name", nullable = false)
    public String getName() {
        return name;
    }
    @Column(name = "department", nullable = false)
    public String getDepartment() {
        return department;
    }

    @Column(name = "phone", nullable = false)
    public String getPhone() {
        return phone;
    }

    @Column(name = "age", nullable = false)
    public int getAge() {
        return age;
    }

    @AiNotQueryable
    @Column(name = "salary")
    public BigDecimal getSalary() {
        return salary;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setAge(int age) {
        this.age = age;
    }
    public void setDepartment(String department) {
        this.department = department;
    }
    public void setPhone(String phone) {
        this.phone = phone;
    }
    public void setSalary(BigDecimal salary) {
        this.salary = salary;
    }

    /** Not a persistent attribute -- must never appear in the AI's queryable field catalog. */
    @Transient
    public String getDisplayLabel() {
        return name + " (" + department + ")";
    }

}
