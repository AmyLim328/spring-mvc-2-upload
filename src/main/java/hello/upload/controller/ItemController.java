package hello.upload.controller;

import hello.upload.domain.Item;
import hello.upload.domain.ItemRepository;
import hello.upload.domain.UploadFile;
import hello.upload.file.FileStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ItemController {

    private final ItemRepository itemRepository;
    private final FileStore fileStore;

    @GetMapping("/items/new") // 등록 폼을 보여준다.
    public String newItem(@ModelAttribute ItemForm form) {
        return "item-form";
    }

    @PostMapping("/items/new") // 폼의 데이터를 저장하고 보여주는 화면으로 리다이렉트 한다.
    public String saveItem(@ModelAttribute ItemForm form, RedirectAttributes redirectAttributes) throws IOException {

//        MultipartFile attachFile = form.getAttachFile();
//        UploadFile uploadFile = fileStore.storeFile(attachFile);
        // inline variable (ctrl + alt + n) : 위의 코드를 한 줄로 합침
        UploadFile attachFile = fileStore.storeFile(form.getAttachFile());

//        List<MultipartFile> imageFiles = form.getImageFiles();
//        List<UploadFile> uploadFiles = fileStore.storeFiles(imageFiles);
        // inline variable (ctrl + alt + n) : 위의 코드를 한 줄로 합침
        List<UploadFile> storeImageFiles = fileStore.storeFiles(form.getImageFiles());

        // => 파일은 파일 저장소에 따로 저장

        // 데이터베이스에 저장 // 파일의 (상대적인) 경로 저장 (경로도 fullpath를 저정하진 않는다) // 파일 자체를 저장 X
        Item item = new Item();
        item.setItemName(form.getItemName());
        item.setAttachFile(attachFile);
        item.setImageFiles(storeImageFiles);
        itemRepository.save(item);

        redirectAttributes.addAttribute("itemId", item.getId());

        return "redirect:/items/{itemId}";
    }

    @GetMapping("items/{id}") // 상품을 보여준다
    public String items(@PathVariable Long id, Model model) {
        Item item = itemRepository.findById(id);
        model.addAttribute("item", item);
        return "item-view";
    }

    @ResponseBody
    @GetMapping("/images/{filename}") // <img>태그로 이미지를 조회할 때 사용한다. UrlResource로 이미지 파일을 읽어서 @ResponseBody 로 이미지 바이너리를 반환한다.
    public Resource downloadImage(@PathVariable String filename) throws MalformedURLException {
        return new UrlResource("file:" + fileStore.getFullPath(filename));
    }
    // 보안에 취약 // 여러가지 체크 로직을 추가하는 것이 좋다

    @GetMapping("/attach/{itemId}") // 파일을 다운로드 할 때 실행한다
    public ResponseEntity<Resource> downloadAttach(@PathVariable Long itemId) throws MalformedURLException {
        Item item = itemRepository.findById(itemId);
        String storeFileName = item.getAttachFile().getStoreFileName();
        String uploadFileName = item.getAttachFile().getUploadFileName(); // 다운받을 때 업로드한 파일명으로 나와야 해서 필요
        // 파일 다운로드 시 권한 체크같은 복잡한 상황까지 가정한다 생각하고 이미지 id 를 요청하도록 했다

        UrlResource resource = new UrlResource("file:" + fileStore.getFullPath(storeFileName));
        log.info("uploadFileName={}", uploadFileName);
        String encodedUploadFileName = UriUtils.encode(uploadFileName, StandardCharsets.UTF_8); // 파일명 깨짐 방지
        String contentDisposition = "attachment; filename=\"" + encodedUploadFileName + "\""; // 필수 규약
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(resource);
    }

}