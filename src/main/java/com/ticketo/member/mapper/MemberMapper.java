package com.ticketo.member.mapper;

import com.ticketo.member.vo.MemberVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 회원 SQL 매퍼. SQL은 {@code resources/mapper/member/MemberMapper.xml}에 정의한다.
 */
@Mapper
public interface MemberMapper {

    /** 비밀번호 컬럼을 제외하고 조회한다. */
    List<MemberVO> selectMemberList();

    /** 비밀번호 컬럼을 제외하고 조회한다. */
    MemberVO selectMemberById(@Param("id") Long id);

    /** 로그인 검증 전용. 비밀번호 해시를 포함해 조회하므로 응답 용도로 쓰지 않는다. */
    MemberVO selectMemberForLoginByEmail(@Param("email") String email);

    boolean existsByEmail(@Param("email") String email);

    /** INSERT 후 생성된 PK가 {@code member.id}에 채워진다. */
    int insertMember(MemberVO member);
}
