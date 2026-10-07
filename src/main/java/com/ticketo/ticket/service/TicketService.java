package com.ticketo.ticket.service;

import com.ticketo.ticket.mapper.TicketMapper;
import com.ticketo.ticket.vo.TicketVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketService {

    private final TicketMapper ticketMapper;

    public List<TicketVO> getTicketList() {
        return ticketMapper.selectTicketList();
    }

    public TicketVO getTicket(Long id) {
        TicketVO ticket = ticketMapper.selectTicketById(id);
        if (ticket == null) {
            throw notFound(id);
        }
        return ticket;
    }

    @Transactional
    public TicketVO createTicket(TicketVO ticket) {
        ticketMapper.insertTicket(ticket);
        return getTicket(ticket.getId());
    }

    @Transactional
    public TicketVO updateTicket(Long id, TicketVO ticket) {
        TicketVO current = getTicket(id);
        ticket.setId(id);
        if (ticketMapper.updateTicket(ticket) == 0) {
            // 존재 확인은 마쳤으므로, 0건 갱신은 판매된 수량보다 총 수량을 줄이려 한 경우
            int sold = current.getTotalQuantity() - current.getRemainingQuantity();
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "이미 판매된 수량(" + sold + "매)보다 총 수량을 줄일 수 없습니다.");
        }
        return getTicket(id);
    }

    @Transactional
    public void deleteTicket(Long id) {
        if (ticketMapper.deleteTicket(id) == 0) {
            throw notFound(id);
        }
    }

    private ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "공연을 찾을 수 없습니다. id=" + id);
    }
}
