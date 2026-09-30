package com.example.addon.notify;

import java.util.List;

public record NotifyMessage(String title, String sender, String avatarPath, long account,
                            long groupId, boolean mention, List<NotifySegment> segments, long born) {
}

