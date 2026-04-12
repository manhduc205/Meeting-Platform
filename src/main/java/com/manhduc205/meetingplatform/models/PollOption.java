package com.manhduc205.meetingplatform.models;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PollOption {
    private String id;
    private String text;
}