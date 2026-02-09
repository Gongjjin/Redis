package com.example.redis.concert;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.time.LocalDate;

public record ConcertResponse(
        Long id,
        String name,
        Integer seats,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")
        LocalDate date
) implements Serializable {
    // Entity -> Record 변환 메서드 (편의상)
    public static ConcertResponse from(Concert concert) {
        return new ConcertResponse(
                concert.getConcertId(),
                concert.getConcertName(),
                concert.getSeats(),
                concert.getConcertDay()
        );
    }
}