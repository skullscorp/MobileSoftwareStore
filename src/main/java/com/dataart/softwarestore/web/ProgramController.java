package com.dataart.softwarestore.web;

import com.dataart.softwarestore.model.domain.Category;
import com.dataart.softwarestore.model.domain.Program;
import com.dataart.softwarestore.model.domain.Statistics;
import com.dataart.softwarestore.model.dto.ProgramForm;
import com.dataart.softwarestore.model.dto.ProgramTextDetails;
import com.dataart.softwarestore.service.CategoryManager;
import com.dataart.softwarestore.service.ProgramManager;
import com.dataart.softwarestore.utils.FtpTransferHandler;
import com.dataart.softwarestore.utils.ProgramInfoHandler;
import com.dataart.softwarestore.utils.ProgramZipFileHandler;
import com.dataart.softwarestore.validation.AfterUploadFilesValidator;
import com.dataart.softwarestore.validation.ProgramFormValidator;
import com.dataart.softwarestore.validation.ProgramTextDetailsValidator;
import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@Controller
public class ProgramController {

    private static final Logger LOG = Logger.getLogger(ProgramController.class);
    private static final String PROGRAM_SUBMIT_PAGE = "submit";
    private static final String PROGRAM_DETAILS_PAGE = "details";
    private static final String REDIRECT_TO_SUBMIT_PAGE = "redirect:/submit";
    private static final Long INITIAL_DOWNLOADS = 0L;
    private static final int FILE_SIZE_DIVIDER = 1024;

    private HttpServletRequest servletRequest;
    private MessageSourceAccessor websiteMessages;
    private ProgramManager programManager;
    private CategoryManager categoryManager;
    private ProgramFormValidator programFormValidator;
    private AfterUploadFilesValidator afterUploadFilesValidator;
    private ProgramZipFileHandler programZipFileHandler;
    private ProgramInfoHandler programInfoHandler;
    private ProgramTextDetailsValidator programTextDetailsValidator;
    private FtpTransferHandler ftpTransferHandler;
    @Value("${uploaded.file.max.size.bytes}")
    private Long uploadedFileMaxSizeBytes;
    @Value("${temp.upload.dir}")
    private String tempUploadDir;
    @Value("${program.zip.inner.txt.info.file}")
    private String zipInnerTxtInfoFile;


    @Autowired
    public ProgramController(HttpServletRequest servletRequest, MessageSourceAccessor websiteMessages, ProgramManager programManager,
                             CategoryManager categoryManager, ProgramFormValidator programFormValidator, AfterUploadFilesValidator afterUploadFilesValidator,
                             ProgramZipFileHandler programZipFileHandler, ProgramInfoHandler programInfoHandler, ProgramTextDetailsValidator programTextDetailsValidator,
                             FtpTransferHandler ftpTransferHandler) {
        this.servletRequest = servletRequest;
        this.websiteMessages = websiteMessages;
        this.programManager = programManager;
        this.categoryManager = categoryManager;
        this.programFormValidator = programFormValidator;
        this.afterUploadFilesValidator = afterUploadFilesValidator;
        this.programZipFileHandler = programZipFileHandler;
        this.programInfoHandler = programInfoHandler;
        this.programTextDetailsValidator = programTextDetailsValidator;
        this.ftpTransferHandler = ftpTransferHandler;
    }

    @InitBinder("programForm")
    private void initProgramFormValidation(WebDataBinder binder) {
        binder.addValidators(programFormValidator);
    }

    @RequestMapping(value = "/submit", method = RequestMethod.GET)
    public String getAddProgramForm(Model model) {
        LOG.debug("Getting program submit form");
        model.addAttribute("programForm", new ProgramForm());
        model.addAttribute("allCategories", categoryManager.getAllCategories());
        model.addAttribute("maxFileSizeKb", uploadedFileMaxSizeBytes / FILE_SIZE_DIVIDER);
        return PROGRAM_SUBMIT_PAGE;
    }

    @RequestMapping(value = "/submit", method = RequestMethod.POST)
    public String submitAddProgramForm(Model model, @ModelAttribute("programForm") @Valid ProgramForm programForm, BindingResult result, RedirectAttributes redirect) {
        model.addAttribute("allCategories", categoryManager.getAllCategories());
        model.addAttribute("maxFileSizeKb", uploadedFileMaxSizeBytes / FILE_SIZE_DIVIDER);
        if (result.hasErrors()) return PROGRAM_SUBMIT_PAGE;

        File uploadedZipFile = null;
        File extractPath = null;
        ProgramTextDetails programTextDetails = null;
        try {
            File mainUploadDir = new File(servletRequest.getSession().getServletContext().getRealPath(tempUploadDir));
            uploadedZipFile = programZipFileHandler.transferFileToDir(programForm.getFile(), mainUploadDir);
            extractPath = new File(FilenameUtils.removeExtension(uploadedZipFile.getAbsolutePath()));
            Map<String, File> extractedFiles = programZipFileHandler.extractZipFile(uploadedZipFile, extractPath);
            if (afterUploadFilesValidator.areThereEmptyFiles(extractedFiles)) {
                LOG.debug("Some extracted files are empty");
                redirect.addFlashAttribute("errorMessage", websiteMessages.getMessage("error.contains.empty.files"));
                return REDIRECT_TO_SUBMIT_PAGE;
            }

            programTextDetails = programInfoHandler.getProgramTextDetails(extractedFiles.get(zipInnerTxtInfoFile));
            if (!programTextDetailsValidator.isValid(programTextDetails)) {
                LOG.debug("Zip inner txt file has wrong format or is missing required fields");
                redirect.addFlashAttribute("errorMessage", websiteMessages.getMessage("error.zip.txt.file.format"));
                return REDIRECT_TO_SUBMIT_PAGE;
            }

            String targetUploadDir = programForm.getName();
            ftpTransferHandler.uploadFiles(extractedFiles, targetUploadDir);
        } catch (IOException e) {
            LOG.error("Error during processing zip file: " + e.getMessage());
            redirect.addFlashAttribute("errorMessage", websiteMessages.getMessage("error.processing.zip"));
            return REDIRECT_TO_SUBMIT_PAGE;
        } finally {
            LOG.debug("Attempting to remove temporary files");
            programZipFileHandler.batchRemoveFiles(uploadedZipFile, extractPath);
        }

        LOG.debug("Adding new program: " + programForm.toString());
        Category category = categoryManager.getCategoryById(programForm.getCategoryId());
        Statistics statistics = new Statistics(LocalDateTime.now(), INITIAL_DOWNLOADS);
        Program newProgram = new Program(programForm.getName(), programForm.getDescription(), programTextDetails.getPicName128().orElse(null),
                programTextDetails.getPicName512().orElse(null), category, statistics);
        programManager.addProgram(newProgram);
        redirect.addFlashAttribute("successMessage", websiteMessages.getMessage("msg.program.added"));
        return REDIRECT_TO_SUBMIT_PAGE;
    }

    @RequestMapping(value = "/details", method = RequestMethod.GET)
    public String getProgramDetailsPage() {
        return PROGRAM_DETAILS_PAGE;
    }

    @RequestMapping(value = "/remove", method = RequestMethod.GET)
    public String removeProgram(@RequestParam("id") Integer id) {
        programManager.removeProgram(id);
        return "/";
    }
}
