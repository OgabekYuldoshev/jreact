package dev.jreact.examples.library.server;

import dev.jreact.examples.library.shared.BookStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface BookRepository extends JpaRepository<BookEntity, Long> {

    /** {@code q} is lower case and may be empty; {@code status} null means all. */
    @Query("""
            select b from BookEntity b
            where (:q = '' or lower(b.title) like concat('%', :q, '%') or lower(b.author) like concat('%', :q, '%'))
              and (:status is null or b.status = :status)
            """)
    Page<BookEntity> search(@Param("q") String q, @Param("status") BookStatus status, Pageable pageable);

    @Query("select b.status, count(b) from BookEntity b group by b.status")
    List<Object[]> countByStatus();
}
