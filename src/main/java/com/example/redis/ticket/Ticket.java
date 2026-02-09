package com.example.redis.ticket;

import com.example.redis.concert.Seat;
import com.example.redis.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ticketId;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "seatId")
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "userId")
    private User user;

    public Ticket(Seat seat, User user) {
        this.seat = seat;
        this.user = user;
    }
}
