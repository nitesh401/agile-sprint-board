package com.agile.board;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
public class BoardApplication {
    public static void main(String[] args) { SpringApplication.run(BoardApplication.class, args); }

    @Bean
    CommandLineRunner seed(SprintRepo sprints, DevRepo devs, StoryRepo stories) {
        return a -> {
            if (sprints.count() > 0) return;
            Sprint s = new Sprint();
            s.name = "Sprint 1"; s.goal = "Ship login and dashboard";
            s.startDate = LocalDate.now(); s.endDate = LocalDate.now().plusDays(14); s.active = true;
            s = sprints.save(s);
            List<Developer> d = new ArrayList<>();
            for (String n : new String[]{"Asha", "Ravi", "Meera"}) { Developer x = new Developer(); x.name = n; d.add(devs.save(x)); }
            String[] t = {"Login API", "Dashboard UI", "Kafka consumer", "Report export", "Role-based access", "Fix pagination bug"};
            Status[] st = Status.values();
            for (int i = 0; i < t.length; i++) {
                Story y = new Story();
                y.title = t[i]; y.description = "Sample story"; y.points = 3 + i % 4;
                y.status = st[i]; y.sprintId = s.id; y.assigneeId = d.get(i % 3).id;
                stories.save(y);
            }
        };
    }
}

enum Status { IN_PROGRESS, DEV_READY, DEV_COMPLETED, QA_READY, QA_COMPLETED, PO_ACCEPTANCE }

@Entity class Sprint {
    @Id @GeneratedValue public Long id;
    public String name, goal;
    public LocalDate startDate, endDate;
    public boolean active;
}

@Entity class Developer {
    @Id @GeneratedValue public Long id;
    public String name;
}

@Entity class Story {
    @Id @GeneratedValue public Long id;
    public String title;
    @Column(length = 2000) public String description;
    public int points;
    @Enumerated(EnumType.STRING) public Status status = Status.IN_PROGRESS;
    public Long sprintId, assigneeId;
}

interface SprintRepo extends JpaRepository<Sprint, Long> {}
interface DevRepo extends JpaRepository<Developer, Long> {}
interface StoryRepo extends JpaRepository<Story, Long> { List<Story> findBySprintId(Long sprintId); }

@RestController
@RequestMapping("/api")
class BoardController {
    private final SprintRepo sprints; private final DevRepo devs; private final StoryRepo stories;
    BoardController(SprintRepo s, DevRepo d, StoryRepo st) { sprints = s; devs = d; stories = st; }

    @GetMapping("/sprints") List<Sprint> sprints() { return sprints.findAll(); }
    @PostMapping("/sprints") Sprint addSprint(@RequestBody Sprint s) { s.id = null; return sprints.save(s); }

    @GetMapping("/developers") List<Developer> devs() { return devs.findAll(); }
    @PostMapping("/developers") Developer addDev(@RequestBody Developer d) { d.id = null; return devs.save(d); }

    @GetMapping("/stories") List<Story> stories(@RequestParam Long sprintId) { return stories.findBySprintId(sprintId); }
    @PostMapping("/stories") Story addStory(@RequestBody Story s) { s.id = null; if (s.status == null) s.status = Status.IN_PROGRESS; return stories.save(s); }
    @PutMapping("/stories/{id}") Story update(@PathVariable Long id, @RequestBody Story s) { s.id = id; return stories.save(s); }
    @PatchMapping("/stories/{id}/status")
    Story move(@PathVariable Long id, @RequestBody Map<String, String> b) {
        Story s = stories.findById(id).orElseThrow();
        s.status = Status.valueOf(b.get("status"));
        return stories.save(s);
    }
    @PatchMapping("/stories/{id}/assignee")
    Story assign(@PathVariable Long id, @RequestBody Map<String, Long> b) {
        Story s = stories.findById(id).orElseThrow();
        s.assigneeId = b.get("assigneeId");
        return stories.save(s);
    }
    @DeleteMapping("/stories/{id}") void delete(@PathVariable Long id) { stories.deleteById(id); }
}
