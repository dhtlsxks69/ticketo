package com.ticketo.reservation.service;

import com.ticketo.member.mapper.MemberMapper;
import com.ticketo.reservation.mapper.ReservationMapper;
import com.ticketo.reservation.vo.ReservationVO;
import com.ticketo.ticket.mapper.TicketMapper;
import com.ticketo.ticket.vo.TicketVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationMapper reservationMapper;
    private final TicketMapper ticketMapper;
    private final MemberMapper memberMapper;

    /**
     * 예매: 회원/공연 존재와 잔여 수량을 확인한 뒤 예매를 저장하고 잔여 수량을 차감한다.
     * 동시성 처리는 하지 않은 기본 버전이다. (조회 후 차감 사이에 다른 요청이 끼어들 수 있음)
     */
    @Transactional
    public ReservationVO reserve(ReservationVO reservation) {
        if (memberMapper.selectMemberById(reservation.getMemberId()) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "회원을 찾을 수 없습니다. id=" + reservation.getMemberId());
        }

        TicketVO ticket = ticketMapper.selectTicketById(reservation.getTicketId());
        if (ticket == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "공연을 찾을 수 없습니다. id=" + reservation.getTicketId());
        }
        if (ticket.getRemainingQuantity() < reservation.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "잔여 수량이 부족합니다. 잔여=" + ticket.getRemainingQuantity()
                            + ", 요청=" + reservation.getQuantity());
        }

        reservationMapper.insertReservation(reservation);
        ticketMapper.updateTicketRemainingQuantity(ticket.getId(), -reservation.getQuantity());
        return reservationMapper.selectReservationById(reservation.getId());
    }
}
