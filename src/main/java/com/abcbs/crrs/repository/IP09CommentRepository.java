package com.abcbs.crrs.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.abcbs.crrs.entity.CommentPK;
import com.abcbs.crrs.entity.P09Comment;

public interface IP09CommentRepository extends JpaRepository<P09Comment, CommentPK> {

}
