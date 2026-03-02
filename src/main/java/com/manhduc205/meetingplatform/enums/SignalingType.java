package com.manhduc205.meetingplatform.enums;

public enum SignalingType {
    OFFER,           // Gửi lời mời P2P
    ANSWER,          // Trả lời P2P
    ICE_CANDIDATE,   // Trao đổi IP/Port
    SWITCH_TO_SFU    // chuyển sang SFU
}