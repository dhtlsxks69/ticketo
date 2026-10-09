package com.ticketo.reservation.mapper;

import com.ticketo.reservation.vo.ReservationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 예매 SQL 매퍼. SQL은 {@code resources/mapper/reservation/ReservationMapper.xml}에 정의한다.
 */
@Mapper
public interface ReservationMapper {

    ReservationVO selectReservationById(@Param("id") Long id);

    /** 회원의 예매 내역을 최신순으로 조회한다. 취소된 예매도 포함한다. */
    List<ReservationVO> selectReservationListByMemberId(@Param("memberId") Long memberId);

    /** INSERT 후 생성된 PK가 {@code reservation.id}에 채워진다. 상태는 DB 기본값(RESERVED)으로 저장된다. */
    int insertReservation(ReservationVO reservation);

    /** 취소 처리. 이미 취소된 예매이거나 없는 id이면 0건이 갱신된다. */
    int updateReservationCanceled(@Param("id") Long id);
}
