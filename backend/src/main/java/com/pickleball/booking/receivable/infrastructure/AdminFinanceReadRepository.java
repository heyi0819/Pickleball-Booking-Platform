package com.pickleball.booking.receivable.infrastructure;

import com.pickleball.booking.receivable.application.FinanceReadViews.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Bounded read projections over the existing finance ledger; never locks or writes it. */
@Repository
public class AdminFinanceReadRepository {
    private final JdbcClient jdbc;
    public AdminFinanceReadRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public boolean organizationExists(UUID id) {
        return jdbc.sql("select exists(select 1 from organizations where id=:id)")
                .param("id", id).query(Boolean.class).single();
    }

    private static final String RECEIVABLE_FROM = """
            from receivables r
            join organizations o on o.id=r.organization_id
            join users u on u.id=r.payer_user_id
            join courses c on c.id=r.course_id and c.organization_id=r.organization_id
            """;
    private static final String RECEIVABLE_SELECT = """
            select r.*, o.name organization_name, u.display_name member_name, c.course_no
            """;
    private static final String PAYMENT_FROM = """
            from payments p join organizations o on o.id=p.organization_id
            join users u on u.id=p.payer_user_id
            """;
    // Same reservation set as RefundStore.findRequestContextLocked; informational, not authorization.
    private static final String PAYMENT_SELECT = """
            select p.*, o.name organization_name, u.display_name member_name,
              p.amount - (select coalesce(sum(f.amount),0) from refunds f
                where f.payment_id=p.id and f.status in ('PENDING_APPROVAL','APPROVED','COMPLETED')) refundable_amount
            """;
    private static final String REFUND_FROM = """
            from refunds f join payments p on p.id=f.payment_id and p.organization_id=f.organization_id
            join organizations o on o.id=f.organization_id join users u on u.id=p.payer_user_id
            """;
    // Excludes this request, as RefundStore.activeReservedRefundAmount does for review.
    private static final String REFUND_SELECT = """
            select f.*, p.payment_no, p.payer_user_id, p.currency, o.name organization_name,
              u.display_name member_name,
              p.amount - (select coalesce(sum(other.amount),0) from refunds other
                where other.payment_id=p.id and other.id<>f.id
                and other.status in ('PENDING_APPROVAL','APPROVED','COMPLETED')) refundable_amount
            """;

    public Page<Receivable> receivables(UUID org, String status, UUID member, UUID course, int page, int size) {
        Filter f = new Filter("r", org).equal("r.status", "status", status)
                .equal("r.payer_user_id", "member", member).equal("r.course_id", "course", course);
        return page(RECEIVABLE_SELECT, RECEIVABLE_FROM, f, "r.created_at", page, size, this::receivable);
    }
    public Optional<Receivable> receivable(UUID org, UUID id) {
        return one(RECEIVABLE_SELECT, RECEIVABLE_FROM, new Filter("r",org).equal("r.id","id",id), this::receivable);
    }
    public Page<Payment> payments(UUID org, String status, UUID member, UUID receivable, int page, int size) {
        Filter f = new Filter("p",org).equal("p.status","status",status).equal("p.payer_user_id","member",member);
        if (receivable != null) f.existsReceivable(receivable);
        var raw = page(PAYMENT_SELECT,PAYMENT_FROM,f,"p.paid_at",page,size,this::payment);
        var refs = references(org,raw.items().stream().map(Payment::id).toList());
        return new Page<>(raw.items().stream().map(p -> withReferences(p,refs.getOrDefault(p.id(),List.of()))).toList(),page,size,raw.totalElements());
    }
    public Optional<Payment> payment(UUID org, UUID id) {
        return one(PAYMENT_SELECT,PAYMENT_FROM,new Filter("p",org).equal("p.id","id",id),this::payment)
                .map(p -> withReferences(p,references(org,List.of(id)).getOrDefault(id,List.of())));
    }
    public Page<Refund> refunds(UUID org, String status, UUID member, UUID payment, int page, int size) {
        Filter f = new Filter("f",org).equal("f.status","status",status)
                .equal("p.payer_user_id","member",member).equal("f.payment_id","payment",payment);
        var raw = page(REFUND_SELECT,REFUND_FROM,f,"f.requested_at",page,size,this::refund);
        var refs = references(org,raw.items().stream().map(Refund::paymentId).distinct().toList());
        return new Page<>(raw.items().stream().map(r -> withReferences(r,refs.getOrDefault(r.paymentId(),List.of()))).toList(),page,size,raw.totalElements());
    }
    public Optional<Refund> refund(UUID org, UUID id) {
        return one(REFUND_SELECT,REFUND_FROM,new Filter("f",org).equal("f.id","id",id),this::refund)
                .map(r -> withReferences(r,references(org,List.of(r.paymentId())).getOrDefault(r.paymentId(),List.of())));
    }
    private <T> Page<T> page(String select, String from, Filter f, String order, int page, int size, RowMapper<T> mapper) {
        long total = jdbc.sql("select count(*) "+from+f.sql).params(f.params).query(Long.class).single();
        var rows = jdbc.sql(select+from+f.sql+" order by "+order+" desc, "+f.alias+".id desc limit :size offset :offset")
                .params(f.params).param("size",size).param("offset",(long)page*size).query(mapper).list();
        return new Page<>(rows,page,size,total);
    }
    private <T> Optional<T> one(String select, String from, Filter f, RowMapper<T> mapper) {
        return jdbc.sql(select+from+f.sql).params(f.params).query(mapper).optional();
    }
    private Map<UUID,List<ReceivableReference>> references(UUID org,List<UUID> payments) {
        if (payments.isEmpty()) return Map.of();
        var rows = jdbc.sql("""
                select distinct pa.payment_id, r.id, r.receivable_no, r.course_id, c.course_no
                from payment_allocations pa join payments p on p.id=pa.payment_id
                join receivable_items ri on ri.id=pa.receivable_item_id
                join receivables r on r.id=ri.receivable_id and r.organization_id=p.organization_id
                join courses c on c.id=r.course_id and c.organization_id=r.organization_id
                where p.organization_id=:org and pa.payment_id in (:payments)
                order by pa.payment_id,r.id
                """).param("org",org).param("payments",payments)
                .query((rs,n) -> Map.entry(uuid(rs,"payment_id"),new ReceivableReference(uuid(rs,"id"),rs.getString("receivable_no"),uuid(rs,"course_id"),rs.getString("course_no")))).list();
        Map<UUID,List<ReceivableReference>> result=new HashMap<>();
        rows.forEach(e -> result.computeIfAbsent(e.getKey(),k -> new ArrayList<>()).add(e.getValue()));
        return result;
    }
    private Receivable receivable(ResultSet r,int n) throws SQLException {
        return new Receivable(uuid(r,"id"),r.getString("receivable_no"),uuid(r,"organization_id"),r.getString("organization_name"),
                uuid(r,"payer_user_id"),r.getString("member_name"),uuid(r,"course_id"),r.getString("course_no"),r.getString("currency"),
                money(r,"total_amount"),money(r,"adjusted_amount"),money(r,"paid_amount"),money(r,"refunded_amount"),
                money(r,"balance_amount"),r.getString("status"),instant(r,"created_at"),instant(r,"due_at"));
    }
    private Payment payment(ResultSet r,int n) throws SQLException {
        return new Payment(uuid(r,"id"),r.getString("payment_no"),uuid(r,"organization_id"),r.getString("organization_name"),
                uuid(r,"payer_user_id"),r.getString("member_name"),money(r,"amount"),r.getString("currency"),r.getString("status"),
                r.getString("payment_method"),instant(r,"paid_at"),instant(r,"created_at"),money(r,"refundable_amount"),List.of());
    }
    private Refund refund(ResultSet r,int n) throws SQLException {
        return new Refund(uuid(r,"id"),r.getString("refund_no"),uuid(r,"organization_id"),r.getString("organization_name"),
                uuid(r,"payment_id"),r.getString("payment_no"),uuid(r,"payer_user_id"),r.getString("member_name"),money(r,"amount"),
                r.getString("currency"),r.getString("status"),r.getString("reason"),instant(r,"requested_at"),instant(r,"approved_at"),
                instant(r,"refunded_at"),money(r,"refundable_amount"),List.of());
    }
    private static Payment withReferences(Payment p,List<ReceivableReference> refs) {
        return new Payment(p.id(),p.paymentNo(),p.organizationId(),p.organizationName(),p.memberId(),p.memberName(),p.amount(),p.currency(),
                p.status(),p.method(),p.paidAt(),p.recordedAt(),p.refundableAmount(),refs);
    }
    private static Refund withReferences(Refund r,List<ReceivableReference> refs) {
        return new Refund(r.id(),r.refundNo(),r.organizationId(),r.organizationName(),r.paymentId(),r.paymentNo(),r.memberId(),r.memberName(),
                r.amount(),r.currency(),r.status(),r.reason(),r.requestedAt(),r.approvedAt(),r.refundedAt(),r.refundableAmount(),refs);
    }
    private static UUID uuid(ResultSet r,String c) throws SQLException { return r.getObject(c,UUID.class); }
    private static String money(ResultSet r,String c) throws SQLException { return r.getBigDecimal(c).toPlainString(); }
    private static Instant instant(ResultSet r,String c) throws SQLException { var t=r.getTimestamp(c); return t==null?null:t.toInstant(); }
    private static final class Filter {
        final String alias; String sql; final Map<String,Object> params=new HashMap<>();
        Filter(String alias,UUID org) { this.alias=alias; sql=" where "+alias+".organization_id=:org"; params.put("org",org); }
        Filter equal(String column,String key,Object value) { if(value!=null) { sql+=" and "+column+"=:"+key; params.put(key,value); } return this; }
        void existsReceivable(UUID id) {
            sql+="""
                     and exists (select 1 from payment_allocations pa join receivable_items ri on ri.id=pa.receivable_item_id
                       join receivables ar on ar.id=ri.receivable_id and ar.organization_id=p.organization_id
                       where pa.payment_id=p.id and ri.receivable_id=:receivable)
                    """;
            params.put("receivable",id);
        }
    }
}
