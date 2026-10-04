package ent.kz.entbackend.platform.importing;

import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MaterialPackController {

  private final MaterialPackService service;

  public MaterialPackController(MaterialPackService service) {
    this.service = service;
  }

  @PostMapping(
    value = "/api/cms/material-packs",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  public Object upload(
    @RequestParam String namespace,
    @RequestParam String externalKey,
    @RequestParam UUID contentId,
    @RequestParam String titleRu,
    @RequestParam(defaultValue = "") String titleKz,
    @RequestParam MultipartFile file
  ) {
    return service.upload(
      namespace,
      externalKey,
      contentId,
      titleRu,
      titleKz,
      file
    );
  }
}
