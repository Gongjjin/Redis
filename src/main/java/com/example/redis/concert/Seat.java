package com.example.redis.concert;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seatId;
    private int seatNum;
    private Boolean isSold;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concertId")
    private Concert concert;

    public Seat(int seatNum, Concert concert) {
        this.concert = concert;
        this.seatNum = seatNum;
        this.isSold = false;
    }

    public void buy(){
        if(this.isSold = true){
            throw new IllegalArgumentException("이미 판매된 좌석입니다.");
        }
        this.isSold = true;
    }
}
