/**
 * Developer: Dharani Parimella
 * Description: This class handles P09Comment entity with composite primary key
 */
package com.abcbs.crrs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name="P09_COMMENT")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class P09Comment {

	@EmbeddedId
	private CommentPK commentId;

	@Column(name = "CMT_USER_ID", nullable = false, length = 7)
	private String cmtUserId;

	@Column(name = "CMT_TEXT", nullable = false, length = 260)
	private String cmtText;

}
