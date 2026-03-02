package com.manhduc205.meetingplatform.enums;

public enum MessageCategory {
    PRESENCE,  // Liên quan đến join/leave phòng
    SIGNALING, // Liên quan đến WebRTC (Offer, Answer, ICE, Switch)
    ACTION     // Liên quan đến nghiệp vụ (Chat, Whiteboard)
}