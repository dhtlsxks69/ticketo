package com.ticketo.ticket.mapper;

import com.ticketo.ticket.vo.TicketVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 공연(티켓) SQL 매퍼. SQL은 {@code resources/mapper/ticket/TicketMapper.xml}에 정의한다.
 */
@Mapper
public interface TicketMapper {

    List<TicketVO> selectTicketList();

    TicketVO selectTicketById(@Param("id") Long id);

    /** INSERT 후 생성된 PK가 {@code ticket.id}에 채워진다. 잔여 수량은 총 수량으로 초기화된다. */
    int insertTicket(TicketVO ticket);

    /**
     * 수정. 총 수량이 바뀌면 잔여 수량도 같은 만큼 증감시키며,
     * 이미 판매된 수량보다 작게 줄이는 경우(잔여 수량이 음수가 되는 경우)에는 0건이 갱신된다.
     */
    int updateTicket(TicketVO ticket);

    int deleteTicket(@Param("id") Long id);
}
