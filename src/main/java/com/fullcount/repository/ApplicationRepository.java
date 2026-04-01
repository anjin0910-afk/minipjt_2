package com.fullcount.repository;

import com.fullcount.domain.Application;
import com.fullcount.domain.ApplicationStatus;
import com.fullcount.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    
    // 특정 게시글의 신청자 목록 (작성자용)
    List<Application> findByPostIdOrderByCreatedAtDesc(Long postId);

    // 내 신청 정보 (신청 유무 확인)
    @Query("SELECT a FROM Application a WHERE a.post.id = :postId AND a.applicant.id = :memberId AND a.status != 'CANCELLED'")
    Optional<Application> findActiveApplication(@Param("postId") Long postId, @Param("memberId") Long memberId);

    // 특정 신청자 아이디로 조회
    Optional<Application> findByPostIdAndApplicantId(Long postId, Long applicantId);
}
