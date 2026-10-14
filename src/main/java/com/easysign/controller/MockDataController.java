package com.easysign.controller;

import com.easysign.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 前端临时联调用虚拟数据 Controller。
 * <p>
 * 所有接口统一挂在 {@code /api/v1/mock/**} 下，避免覆盖正式业务接口。
 * 该类只返回内存中的固定样例数据，不访问数据库、不写入业务表。
 * 前后端联调完成后，应按 docs/虚拟数据接口说明文档.md 删除本类及拦截器白名单。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/mock")
public class MockDataController {

    private static final List<Map<String, Object>> EVENTS = List.of(
            event(1001L, "2026 校园科技展", "AI、机器人与智能硬件集中展示，适合科技爱好者参与。", "创新楼一层展厅", "2026-09-15 09:00:00", "2026-09-15 17:00:00", 200, 126, "online", "2026-07-01 10:00:00", "2026-07-08 09:20:00", "根据你的科技偏好推荐"),
            event(1002L, "夏夜草坪音乐会", "校园乐队、民谣社与说唱社联合演出，开放免费预约。", "梅园草坪", "2026-09-20 19:30:00", "2026-09-20 21:30:00", 300, 244, "online", "2026-07-02 11:00:00", "2026-07-08 12:00:00", "音乐类活动热度较高"),
            event(1003L, "电竞社冠军挑战赛", "王者荣耀与英雄联盟表演赛，现场设观赛互动区。", "大学生活动中心", "2026-10-02 14:00:00", "2026-10-02 18:00:00", 160, 98, "draft", "2026-07-03 13:30:00", "2026-07-08 16:45:00", "电竞标签匹配"),
            event(1004L, "学术写作工作坊", "面向本科生的论文选题、文献检索与写作规范训练。", "图书馆报告厅", "2026-10-10 15:00:00", null, 80, 42, "offline", "2026-07-04 09:00:00", "2026-07-08 18:20:00", "学术活动稳定推荐"),
            event(1005L, "公益志愿开放日", "了解校内志愿服务项目，现场完成志愿者报名咨询。", "青协服务站", "2026-08-28 10:00:00", "2026-08-28 16:00:00", 120, 120, "finished", "2026-06-20 12:30:00", "2026-08-28 16:05:00", "历史热门公益活动")
    );

    private static final List<Map<String, Object>> TICKETS = List.of(
            ticket(501L, 1001L, "1001_15_1789443600", "2026 校园科技展", "创新楼一层展厅", "2026-09-15 09:00:00", "2026-09-15 17:00:00", "online", "pending", "2026-07-08 14:30:00", null, "alice"),
            ticket(502L, 1002L, "1002_15_1789903800", "夏夜草坪音乐会", "梅园草坪", "2026-09-20 19:30:00", "2026-09-20 21:30:00", "online", "checked", "2026-07-08 15:10:00", "2026-09-20 19:05:00", "alice"),
            ticket(503L, 1005L, "1005_15_1787872800", "公益志愿开放日", "青协服务站", "2026-08-28 10:00:00", "2026-08-28 16:00:00", "finished", "invalid", "2026-07-08 16:20:00", null, "alice")
    );

    @PostMapping("/auth/login")
    public Result<Map<String, Object>> login(@RequestBody(required = false) Map<String, Object> body) {
        String role = stringValue(body, "role", "attendee");
        String username = stringValue(body, "username", "attendee_demo");
        return Result.success("登录成功", authUser(role, username));
    }

    @PostMapping("/auth/register")
    public Result<Map<String, Object>> register(@RequestBody(required = false) Map<String, Object> body) {
        String role = stringValue(body, "role", "attendee");
        String username = stringValue(body, "username", "new_user");
        return Result.success("注册成功", authUser(role, username));
    }

    @GetMapping("/user/profile")
    public Result<Map<String, Object>> profile(@RequestParam(defaultValue = "attendee") String role) {
        Map<String, Object> profile = mapOf(
                "userId", "organizer".equals(role) ? 2L : 1L,
                "username", "organizer".equals(role) ? "organizer_demo" : "attendee_demo",
                "role", role,
                "gender", "organizer".equals(role) ? null : "female",
                "tags", "organizer".equals(role) ? null : List.of("科技", "音乐", "学术"),
                "avatarUrl", "default.png",
                "createdAt", "2026-07-01 10:00:00"
        );
        return Result.success(profile);
    }

    @PutMapping("/user/profile")
    public Result<Object> updateProfile(@RequestBody Map<String, Object> body) {
        return Result.success("更新成功", null);
    }

    @GetMapping("/event/list")
    public Result<Map<String, Object>> listEvents(
            @RequestParam(defaultValue = "public") String scope,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Map<String, Object>> filtered = EVENTS.stream()
                .filter(event -> !"public".equals(scope) || "online".equals(event.get("status")))
                .toList();
        return Result.success(pageOf(filtered, page, size));
    }

    @GetMapping("/event/{eventId}")
    public Result<Map<String, Object>> eventDetail(@PathVariable Long eventId) {
        return Result.success(EVENTS.stream()
                .filter(event -> eventId.equals(event.get("eventId")))
                .findFirst()
                .orElse(EVENTS.get(0)));
    }

    @PostMapping("/event/{eventId}/ai-description")
    public Result<Map<String, Object>> aiDescription(
            @PathVariable Long eventId,
            @RequestBody(required = false) Map<String, Object> body) {
        String title = stringValue(body, "title", "校园活动");
        return Result.success("生成成功", mapOf(
                "description", "✨ " + title + " 即将开启！这里有高质量内容、轻松参与方式和实时签到体验，欢迎同学们预约参与。"
        ));
    }

    @GetMapping("/dashboard/overview")
    public Result<Map<String, Object>> dashboardOverview() {
        Map<String, Object> charts = mapOf(
                "saleRatio", mapOf("type", "pie", "data", List.of(
                        mapOf("name", "校园科技展", "value", 126),
                        mapOf("name", "草坪音乐会", "value", 244),
                        mapOf("name", "电竞挑战赛", "value", 98)
                )),
                "weeklyTrend", mapOf("type", "line", "data", mapOf(
                        "dates", List.of("07-03", "07-04", "07-05", "07-06", "07-07", "07-08", "07-09"),
                        "values", List.of(8, 13, 20, 26, 31, 44, 52)
                )),
                "weeklyOperations", mapOf("type", "line", "data", mapOf(
                        "dates", List.of("07-03", "07-04", "07-05", "07-06", "07-07", "07-08", "07-09"),
                        "events", List.of(1, 0, 2, 1, 0, 1, 2),
                        "sales", List.of(34, 46, 58, 71, 83, 96, 112),
                        "checkins", List.of(8, 13, 20, 26, 31, 44, 52)
                )),
                "genderRatio", mapOf("type", "pie", "data", List.of(
                        mapOf("name", "男", "value", 140),
                        mapOf("name", "女", "value", 172)
                ))
        );

        List<Map<String, Object>> events = EVENTS.stream()
                .map(event -> mapOf(
                        "eventId", event.get("eventId"),
                        "title", event.get("title"),
                        "totalTickets", event.get("totalTickets"),
                        "soldTickets", event.get("soldTickets"),
                        "checkedIn", checkedCount((Long) event.get("eventId")),
                        "status", event.get("status")
                ))
                .toList();

        return Result.success(mapOf(
                "hasOnlineEvent", true,
                "totalSoldTickets", 630,
                "totalCheckedIn", 187,
                "eventCount", EVENTS.size(),
                "events", events,
                "charts", charts
        ));
    }

    @GetMapping("/dashboard/event/{eventId}")
    public Result<Map<String, Object>> dashboardEventDetail(@PathVariable Long eventId) {
        Map<String, Object> event = EVENTS.stream()
                .filter(e -> eventId.equals(e.get("eventId")))
                .findFirst()
                .orElse(EVENTS.get(0));

        // 获取该活动的签到明细
        List<Map<String, Object>> checkinDetails = TICKETS.stream()
                .filter(ticket -> eventId.equals(ticket.get("eventId")))
                .map(ticket -> mapOf(
                        "ticketId", ticket.get("ticketId"),
                        "ticketNo", ticket.get("ticketNo"),
                        "username", ticket.get("username"),
                        "status", ticket.get("status"),
                        "createdAt", ticket.get("createdAt"),
                        "checkedAt", ticket.get("checkedAt")
                ))
                .toList();

        int checkedIn = (int) checkinDetails.stream()
                .filter(d -> "checked".equals(d.get("status")))
                .count();

        return Result.success(mapOf(
                "eventId", event.get("eventId"),
                "title", event.get("title"),
                "startTime", event.get("startTime"),
                "location", event.get("location"),
                "status", event.get("status"),
                "totalTickets", event.get("totalTickets"),
                "soldTickets", event.get("soldTickets"),
                "checkedIn", checkedIn,
                "charts", mapOf(
                        "weeklyTrend", mapOf("type", "line", "data", mapOf(
                                "dates", List.of("07-03", "07-04", "07-05", "07-06", "07-07", "07-08", "07-09"),
                                "values", List.of(3, 5, 8, 10, 12, 11, 7)
                        )),
                        "genderRatio", mapOf("type", "pie", "data", List.of(
                                mapOf("name", "男", "value", 70),
                                mapOf("name", "女", "value", 50)
                        ))
                ),
                "checkinDetails", checkinDetails
        ));
    }

    @GetMapping("/dashboard/prediction/{eventId}")
    public Result<Map<String, Object>> prediction(@PathVariable Long eventId) {
        return Result.success(mapOf(
                "eventId", eventId,
                "eventTitle", titleOf(eventId),
                "predictedArrivalRate", 0.86,
                "reason", "售票率较高，且同类校园活动历史到场率稳定，预计到达率为 86%。",
                "suggestion", "建议提前 30 分钟开放签到通道，并准备人工票号兜底核验。"
        ));
    }

    @GetMapping("/recommend/events")
    public Result<Map<String, Object>> recommendEvents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "3") int size) {
        List<Map<String, Object>> recommended = EVENTS.stream()
                .filter(event -> "online".equals(event.get("status")))
                .map(event -> new LinkedHashMap<>(event))
                .peek(event -> event.put("reason", "根据你的偏好标签与活动热度推荐"))
                .collect(java.util.stream.Collectors.toList());
        return Result.success(pageOf(recommended, page, size));
    }

    @PostMapping("/ticket/purchase")
    public Result<Map<String, Object>> purchase(@RequestBody(required = false) Map<String, Object> body) {
        Long eventId = longValue(body, "eventId", 1001L);
        return Result.success("抢票成功！请前往「我的票夹」查看", mapOf(
                "ticketId", 9001L,
                "ticketNo", eventId + "_15_1789443600",
                "eventTitle", titleOf(eventId),
                "status", "pending"
        ));
    }

    @GetMapping("/ticket/my-tickets")
    public Result<Map<String, Object>> myTickets(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Map<String, Object>> filtered = TICKETS.stream()
                .filter(ticket -> status == null || status.isBlank() || status.equals(ticket.get("status")))
                .toList();
        return Result.success(pageOf(filtered, page, size));
    }

    @GetMapping("/ticket/{ticketId}")
    public Result<Map<String, Object>> ticketDetail(@PathVariable Long ticketId) {
        return Result.success(TICKETS.stream()
                .filter(ticket -> ticketId.equals(ticket.get("ticketId")))
                .findFirst()
                .orElse(TICKETS.get(0)));
    }

    @PostMapping("/ticket/checkin")
    public Result<Map<String, Object>> checkin(@RequestBody(required = false) Map<String, Object> body) {
        String code = stringValue(body, "code", "ticket_1001_1001_15_1789443600_1789443660");
        return Result.success("签到成功", mapOf(
                "ticketId", 9001L,
                "ticketNo", code.replaceFirst("^ticket_", ""),
                "eventTitle", "2026 校园科技展",
                "username", "attendee_demo",
                "checkedAt", "2026-09-15 08:55:30"
        ));
    }

    @GetMapping("/ticket/event/{eventId}/checkins")
    public Result<Map<String, Object>> checkins(
            @PathVariable Long eventId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Map<String, Object>> records = TICKETS.stream()
                .filter(ticket -> eventId.equals(ticket.get("eventId")))
                .filter(ticket -> status == null || status.isBlank() || status.equals(ticket.get("status")))
                .map(ticket -> mapOf(
                        "ticketId", ticket.get("ticketId"),
                        "ticketNo", ticket.get("ticketNo"),
                        "username", ticket.get("username"),
                        "status", ticket.get("status"),
                        "createdAt", ticket.get("createdAt"),
                        "checkedAt", ticket.get("checkedAt")
                ))
                .toList();
        return Result.success(pageOf(records, page, size));
    }

    private static Map<String, Object> authUser(String role, String username) {
        return mapOf(
                "token", "mock-token-" + role,
                "userId", "organizer".equals(role) ? 2L : 1L,
                "username", username,
                "role", role
        );
    }

    private static Map<String, Object> event(
            Long eventId, String title, String description, String location,
            String startTime, String endTime, int totalTickets, int soldTickets,
            String status, String createdAt, String updatedAt, String reason) {
        return mapOf(
                "eventId", eventId,
                "title", title,
                "description", description,
                "location", location,
                "startTime", startTime,
                "endTime", endTime,
                "totalTickets", totalTickets,
                "soldTickets", soldTickets,
                "status", status,
                "createdAt", createdAt,
                "updatedAt", updatedAt,
                "reason", reason
        );
    }

    private static Map<String, Object> ticket(
            Long ticketId, Long eventId, String ticketNo, String eventTitle,
            String eventLocation, String eventStartTime, String eventEndTime,
            String eventStatus, String status, String createdAt, String checkedAt,
            String username) {
        return mapOf(
                "ticketId", ticketId,
                "eventId", eventId,
                "ticketNo", ticketNo,
                "eventTitle", eventTitle,
                "eventLocation", eventLocation,
                "eventStartTime", eventStartTime,
                "eventEndTime", eventEndTime,
                "eventStatus", eventStatus,
                "status", status,
                "createdAt", createdAt,
                "checkedAt", checkedAt,
                "username", username
        );
    }

    private static Map<String, Object> pageOf(List<Map<String, Object>> source, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int from = Math.min((safePage - 1) * safeSize, source.size());
        int to = Math.min(from + safeSize, source.size());
        return mapOf(
                "records", source.subList(from, to),
                "total", source.size(),
                "page", safePage,
                "size", safeSize,
                "pages", (source.size() + safeSize - 1) / safeSize
        );
    }

    private static int checkedCount(Long eventId) {
        return (int) TICKETS.stream()
                .filter(ticket -> eventId.equals(ticket.get("eventId")))
                .filter(ticket -> "checked".equals(ticket.get("status")))
                .count();
    }

    private static String titleOf(Long eventId) {
        return EVENTS.stream()
                .filter(event -> eventId.equals(event.get("eventId")))
                .map(event -> String.valueOf(event.get("title")))
                .findFirst()
                .orElse("模拟活动");
    }

    private static String stringValue(Map<String, Object> body, String key, String defaultValue) {
        if (body == null || body.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(body.get(key));
    }

    private static Long longValue(Map<String, Object> body, String key, Long defaultValue) {
        if (body == null || body.get(key) == null) {
            return defaultValue;
        }
        Object value = body.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
