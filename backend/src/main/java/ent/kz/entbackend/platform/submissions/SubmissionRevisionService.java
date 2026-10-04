package ent.kz.entbackend.platform.submissions;

import static ent.kz.entbackend.platform.PlatformException.require;

import ent.kz.entbackend.platform.*;
import ent.kz.entbackend.platform.content.ContentDtos.Page;
import ent.kz.entbackend.platform.materials.FileDigests;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SubmissionRevisionService {

  private final SubmissionFileRepository repo;
  private final SubmissionAccess access;
  private final Actor actor;

  public SubmissionRevisionService(
    SubmissionFileRepository repo,
    SubmissionAccess access,
    Actor actor
  ) {
    this.repo = repo;
    this.access = access;
    this.actor = actor;
  }

  public Map<String, Object> submit(
    UUID assignment,
    String text,
    List<UUID> fileIds,
    UUID requestKey,
    Long expectedRevision
  ) {
    access.upload(assignment);
    UUID user = actor.id();
    repo.lockUser(user);
    text = Objects.requireNonNullElse(text, "");
    var files = fileIds == null ? List.<UUID>of() : fileIds;
    require(
      text.length() <= 20000 &&
        files.size() <= 5 &&
        files.stream().noneMatch(Objects::isNull) &&
        new HashSet<>(files).size() == files.size(),
      "INVALID_SUBMISSION"
    );
    require(!text.isBlank() || !files.isEmpty(), "EMPTY_SUBMISSION");
    require(
      files.isEmpty() || (requestKey != null && expectedRevision != null),
      "FILE_SUBMISSION_REVISION_REQUIRED"
    );
    List<UUID> ordered = files.stream().sorted().toList();
    String hash = FileDigests.sha256(
      (assignment + "\n" + text + "\n" + ordered).getBytes(
        StandardCharsets.UTF_8
      )
    );
    if (requestKey != null) {
      var previous = repo
        .jdbc()
        .queryForList(
          "SELECT id,request_hash,content_revision,assignment_id FROM submission_revisions WHERE user_id=? AND request_key=?",
          user,
          requestKey
        );
      if (!previous.isEmpty()) {
        var old = previous.getFirst();
        if (
          !old.get("request_hash").equals(hash) ||
          !old.get("assignment_id").equals(assignment)
        ) throw new PlatformException(409, "REQUEST_KEY_REUSED");
        return Map.of(
          "saved",
          true,
          "submissionId",
          old.get("id"),
          "contentRevision",
          old.get("content_revision")
        );
      }
    }
    var current = repo
      .jdbc()
      .queryForList(
        "SELECT * FROM assignment_submissions WHERE assignment_id=? AND user_id=? FOR UPDATE",
        assignment,
        user
      );
    long optimistic = current.isEmpty()
      ? 0
      : ((Number) current.getFirst().get("revision")).longValue();
    if (
      expectedRevision != null && expectedRevision != optimistic
    ) throw new PlatformException(409, "REVISION_CONFLICT");
    for (UUID file : ordered) {
      var row = repo.get(file, true);
      if (
        !row.get("user_id").equals(user) ||
        !row.get("assignment_id").equals(assignment)
      ) throw PlatformException.missing();
      if (!row.get("scan_status").equals("CLEAN")) throw new PlatformException(
        409,
        "FILE_NOT_CLEAN"
      );
    }
    long contentRevision = current.isEmpty()
      ? 1
      : ((Number) current.getFirst().get("content_revision")).longValue() + 1;
    UUID id = UUID.randomUUID();
    repo
      .jdbc()
      .update(
        "INSERT INTO submission_revisions(id,assignment_id,user_id,content_revision,text,request_key,request_hash) VALUES (?,?,?,?,?,?,?)",
        id,
        assignment,
        user,
        contentRevision,
        text,
        requestKey,
        hash
      );
    for (UUID file : ordered)
      repo
        .jdbc()
        .update(
          "INSERT INTO submission_revision_files(submission_id,file_id) VALUES (?,?)",
          id,
          file
        );
    repo.jdbc().update(
      """
      INSERT INTO assignment_submissions(assignment_id,user_id,text,content_revision,latest_submission_id) VALUES (?,?,?,?,?)
      ON CONFLICT(assignment_id,user_id) DO UPDATE SET text=excluded.text,content_revision=excluded.content_revision,latest_submission_id=excluded.latest_submission_id,
      revision=assignment_submissions.revision+1,submitted_at=now(),score=null,feedback=null,graded_by=null,graded_at=null
      """,
      assignment,
      user,
      text,
      contentRevision,
      id
    );
    return Map.of(
      "saved",
      true,
      "submissionId",
      id,
      "contentRevision",
      contentRevision
    );
  }

  public Map<String, Object> current(UUID assignment, UUID user) {
    var rows = repo
      .jdbc()
      .queryForList(
        "SELECT text,submitted_at AS \"submittedAt\",score,feedback,revision,content_revision AS \"contentRevision\",latest_submission_id AS \"submissionId\" FROM assignment_submissions WHERE assignment_id=? AND user_id=?",
        assignment,
        user
      );
    if (rows.isEmpty()) return null;
    var row = rows.getFirst();
    row.put("files", repo.files((UUID) row.get("submissionId")));
    return row;
  }

  public void recordGrade(
    UUID assignment,
    UUID user,
    int score,
    String feedback,
    int maxScore
  ) {
    var row = repo
      .jdbc()
      .queryForMap(
        "SELECT latest_submission_id FROM assignment_submissions WHERE assignment_id=? AND user_id=?",
        assignment,
        user
      );
    UUID revision = (UUID) row.get("latest_submission_id");
    var files = repo
      .jdbc()
      .queryForList(
        "SELECT f.scan_status FROM submission_revision_files r JOIN submission_files f ON f.id=r.file_id WHERE r.submission_id=? ORDER BY f.id FOR UPDATE OF f",
        String.class,
        revision
      );
    if (
      files.stream().anyMatch(status -> !status.equals("CLEAN"))
    ) throw new PlatformException(409, "SUBMISSION_FILES_NOT_CLEAN");
    repo
      .jdbc()
      .update(
        "INSERT INTO submission_grade_history(submission_id,score,max_score,feedback,graded_by) VALUES (?,?,?,?,?)",
        revision,
        score,
        maxScore,
        feedback,
        actor.id()
      );
  }

  public Page<Map<String, Object>> history(
    UUID assignment,
    UUID requestedUser,
    int page
  ) {
    require(page >= 0 && page <= 10000, "INVALID_PAGE");
    UUID user = requestedUser == null ? actor.id() : requestedUser;
    access.read(assignment, user);
    var rows = repo
      .jdbc()
      .queryForList(
        "SELECT id,content_revision AS \"contentRevision\",text,submitted_at AS \"submittedAt\",legacy_imported AS \"legacyImported\" FROM submission_revisions WHERE assignment_id=? AND user_id=? ORDER BY content_revision DESC LIMIT 10 OFFSET ?",
        assignment,
        user,
        page * 10
      );
    for (var row : rows) {
      UUID id = (UUID) row.get("id");
      row.put("files", repo.files(id));
      row.put(
        "grades",
        repo
          .jdbc()
          .queryForList(
            "SELECT score,max_score AS \"maxScore\",feedback,graded_at AS \"gradedAt\",legacy_imported AS \"legacyImported\" FROM submission_grade_history WHERE submission_id=? ORDER BY graded_at DESC,id DESC",
            id
          )
      );
    }
    return new Page<>(
      rows,
      page,
      10,
      repo
        .jdbc()
        .queryForObject(
          "SELECT count(*) FROM submission_revisions WHERE assignment_id=? AND user_id=?",
          Long.class,
          assignment,
          user
        )
    );
  }
}
