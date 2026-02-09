package com.example.redis.concert;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;


@Entity
@Getter
@NoArgsConstructor
@Table(indexes = @Index(name = "idx_concert_name", columnList = "concertName"))
public class Concert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long concertId;
    private String concertName;
    private LocalDate concertDay;
    private Integer seats;
    @Lob
    private String dummyDescription;

    public Concert(String concertName, LocalDate concertDay, Integer seats) {
        this.concertName = concertName;
        this.concertDay = concertDay;
        this.seats = seats;
        this.dummyDescription = "A".repeat(1000);
    }
}
