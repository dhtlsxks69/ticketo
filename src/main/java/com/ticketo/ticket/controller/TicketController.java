package com.ticketo.ticket.controller;

import com.ticketo.ticket.service.TicketService;
import com.ticketo.ticket.vo.TicketVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    /** 공연 목록 조회 (공연 일시 오름차순) */
    @GetMapping
    public List<TicketVO> list() {
        return ticketService.getTicketList();
    }

    /** 공연 상세 조회 */
    @GetMapping("/{id}")
    public TicketVO get(@PathVariable Long id) {
        return ticketService.getTicket(id);
    }

    /** 공연 등록 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketVO create(@Valid @RequestBody TicketVO ticket) {
        return ticketService.createTicket(ticket);
    }

    /** 공연 수정 */
    @PutMapping("/{id}")
    public TicketVO update(@PathVariable Long id, @Valid @RequestBody TicketVO ticket) {
        return ticketService.updateTicket(id, ticket);
    }

    /** 공연 삭제 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        ticketService.deleteTicket(id);
    }
}
