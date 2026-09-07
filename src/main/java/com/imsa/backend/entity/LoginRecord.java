package com.imsa.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "login_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "login_time", nullable = false, updatable = false)
    private LocalDateTime loginTime;

    @Column(nullable = true)
    private String location;

    @Column(name = "ip_address", nullable = true)
    private String ipAddress;

    @Column(name = "device_info", nullable = true)
    private String deviceInfo;

    @Column(name = "network_type", nullable = true)
    private String networkType;

    @Column(nullable = true)
    private String remark;

    @Column(name = "client_request_id", unique = true, nullable = true)
    private String clientRequestId;
}
