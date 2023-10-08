package com.tarento.upsmf.examsAndAdmissions.service;

import com.tarento.upsmf.examsAndAdmissions.enums.ResultStatus;
import com.tarento.upsmf.examsAndAdmissions.enums.RetotallingStatus;
import com.tarento.upsmf.examsAndAdmissions.model.*;
import com.tarento.upsmf.examsAndAdmissions.model.dto.ExamResultDTO;
import com.tarento.upsmf.examsAndAdmissions.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.persistence.EntityNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StudentResultService {

    @Autowired
    private StudentResultRepository studentResultRepository;
    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ExamRepository examRepository;
    @Autowired
    private RetotallingRequestRepository retotallingRequestRepository;

    public void importInternalMarksFromExcel(MultipartFile file) throws IOException {
        // Parse the Excel file
        List<StudentResult> results = parseExcel(file);

        // For each result, update or create the record with internal marks
        for (StudentResult result : results) {
            Optional<StudentResult> existingResult = Optional.ofNullable(studentResultRepository.findByStudent_EnrollmentNumber(result.getStudent().getEnrollmentNumber()));

            if (existingResult.isPresent()) {
                StudentResult dbResult = existingResult.get();
                dbResult.setInternalMarksObtained(result.getInternalMarksObtained());
                dbResult.setPracticalMarksObtained(result.getPracticalMarksObtained());
                studentResultRepository.save(dbResult);
            } else {
                studentResultRepository.save(result);
            }
        }
    }

    public void importExternalMarksFromExcel(MultipartFile file) throws IOException {
        // Parse the Excel file
        List<StudentResult> results = parseExcel(file);

        // For each result, update or create the record with external marks
        for (StudentResult result : results) {
            Optional<StudentResult> existingResult = Optional.ofNullable(studentResultRepository.findByStudent_EnrollmentNumber(result.getStudent().getEnrollmentNumber()));

            if (existingResult.isPresent()) {
                StudentResult dbResult = existingResult.get();
                dbResult.setExternalMarksObtained(result.getExternalMarksObtained());
                studentResultRepository.save(dbResult);
            } else {
                studentResultRepository.save(result);
            }
        }
    }

    private List<StudentResult> parseExcel(MultipartFile file) throws IOException {
        List<StudentResult> resultList = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
            XSSFSheet sheet = workbook.getSheetAt(0);

            Iterator<Row> rowIterator = sheet.iterator();
            if (rowIterator.hasNext()) rowIterator.next(); // skip header row

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                if (isRowEmpty(row)) {
                    continue;
                }
                StudentResult result = new StudentResult();

                try {
                    Student studentEntity = fetchStudentByEnrollmentNumber(getStringValue(row.getCell(2)));
                    Course courseEntity = fetchCourseByName(getStringValue(row.getCell(5)));
                    Exam examEntity = fetchExamByName(getStringValue(row.getCell(7)));

                    result.setStudent(studentEntity);
                    result.setCourse(courseEntity);
                    result.setExam(examEntity);
                    result.setInternalMarks(getBigDecimalValue(row.getCell(8)));
                    result.setPassingInternalMarks(getBigDecimalValue(row.getCell(9)));
                    result.setInternalMarksObtained(getBigDecimalValue(row.getCell(10)));

                    result.setExternalMarks(getBigDecimalValue(row.getCell(11)));
                    result.setPassingExternalMarks(getBigDecimalValue(row.getCell(12)));
                    result.setExternalMarksObtained(getBigDecimalValue(row.getCell(13)));

                    result.setPracticalMarks(getBigDecimalValue(row.getCell(14)));
                    result.setPassingPracticalMarks(getBigDecimalValue(row.getCell(15)));
                    result.setPracticalMarksObtained(getBigDecimalValue(row.getCell(16)));

                    result.setOtherMarks(getBigDecimalValue(row.getCell(17)));
                    result.setPassingOtherMarks(getBigDecimalValue(row.getCell(18)));
                    result.setOtherMarksObtained(getBigDecimalValue(row.getCell(19)));

                    result.setTotalMarks(getBigDecimalValue(row.getCell(20)));
                    result.setPassingTotalMarks(getBigDecimalValue(row.getCell(21)));
                    result.setTotalMarksObtained(getBigDecimalValue(row.getCell(22)));
                    result.setGrade(getStringValue(row.getCell(23)));
                    result.setResult(getStringValue(row.getCell(24)));

                    if (validateStudentResult(result)) {
                        resultList.add(result);
                    } else {
                        errors.add("Validation failed for enrolment number: " + studentEntity.getEnrollmentNumber());
                    }
                } catch (Exception e) {
                    log.error("Error processing row in Excel file", e);
                    errors.add("Error processing row for enrolment number: " + getStringValue(row.getCell(0)));
                }
            }
        } catch (Exception e) {
            log.error("Error parsing Excel file", e);
            throw new RuntimeException("Failed to parse the Excel file", e);
        }

        if (!errors.isEmpty()) {
            errors.forEach(log::warn);
            throw new RuntimeException("Failed to process some rows in the Excel file. Check logs for more details.");
        }

        return resultList;
    }
    public static boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK && StringUtils.isNotBlank(cell.toString())) {
                return false;
            }
        }
        return true;
    }

    public StudentResult getStudentResult(Long id) {
        return studentResultRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Result not found with ID: " + id));
    }

    public List<StudentResult> getAllStudentResults() {
        return studentResultRepository.findAll();
    }
    public Student fetchStudentByEnrollmentNumber(String enrollmentNumber) {
        return studentRepository.findByEnrollmentNumber(enrollmentNumber)
                .orElseThrow(() -> new RuntimeException("Student not found with enrollment number: " + enrollmentNumber));
    }

    private Course fetchCourseByName(String courseName) {
        return courseRepository.findByCourseName(courseName)
                .orElseThrow(() -> new RuntimeException("Course not found with name: " + courseName));
    }

    private Exam fetchExamByName(String examName) {
        return examRepository.findByExamName(examName)
                .orElseThrow(() -> new RuntimeException("Exam not found with name: " + examName));
    }
    private String getStringValue(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return String.valueOf((int) cell.getNumericCellValue());
            default:
                return null;
        }
    }

    private BigDecimal getBigDecimalValue(Cell cell) {
        if (cell == null || cell.getCellType() != CellType.NUMERIC) return null;
        return BigDecimal.valueOf(cell.getNumericCellValue());
    }
    private boolean validateStudentResult(StudentResult result) {
        // Validate mandatory fields
        if (result.getStudent() == null || result.getCourse() == null || result.getExam() == null) {
            return false;
        }

        // Validate marks
        if (!isValidMark(result.getInternalMarks())
                || !isValidMark(result.getPassingInternalMarks())
                || !isValidMark(result.getInternalMarksObtained())
                || !isValidMark(result.getExternalMarks())
                || !isValidMark(result.getPassingExternalMarks())
                || !isValidMark(result.getExternalMarksObtained())
                || !isValidMark(result.getPracticalMarks())
                || !isValidMark(result.getPassingPracticalMarks())
                || !isValidMark(result.getPracticalMarksObtained())
                || !isValidMark(result.getOtherMarks())
                || !isValidMark(result.getPassingOtherMarks())
                || !isValidMark(result.getOtherMarksObtained())
                || !isValidMark(result.getTotalMarks())
                || !isValidMark(result.getPassingTotalMarks())
                || !isValidMark(result.getTotalMarksObtained())) {
            return false;
        }
        return true;
    }

    private boolean isValidMark(BigDecimal mark) {
        if (mark == null) {
            return true;  // Assuming marks can be null (not entered)
        }
        return mark.compareTo(BigDecimal.ZERO) >= 0 && mark.compareTo(new BigDecimal("100")) <= 0;
    }
    public void publishResultsForCourseWithinCycle(Long courseId, Long examCycleId) {
        List<StudentResult> resultsForCourse = studentResultRepository.findByCourse_IdAndExam_ExamCycleIdAndPublished(courseId, examCycleId, false);
        for (StudentResult result : resultsForCourse) {
            result.setPublished(true);
        }
        studentResultRepository.saveAll(resultsForCourse);
    }
    public StudentResult findByEnrollmentNumberAndDateOfBirth(String enrollmentNumber, LocalDate dateOfBirth) {
        return studentResultRepository.findByStudent_EnrollmentNumberAndStudent_DateOfBirthAndPublished(enrollmentNumber, dateOfBirth, true);
    }
    public void updateResultAfterRetotalling(StudentResult updatedResult) {
        if (updatedResult.getId() == null) {
            throw new IllegalArgumentException("Updated result must have a valid ID");
        }

        // Fetch existing result from the database
        StudentResult existingResult = studentResultRepository.findById(updatedResult.getId())
                .orElseThrow(() -> new EntityNotFoundException("No StudentResult found with ID " + updatedResult.getId()));

        existingResult.setInternalMarksObtained(updatedResult.getInternalMarksObtained());
        existingResult.setExternalMarksObtained(updatedResult.getExternalMarksObtained());
        existingResult.setPracticalMarksObtained(updatedResult.getPracticalMarksObtained());
        existingResult.setOtherMarksObtained(updatedResult.getOtherMarksObtained());
        existingResult.setTotalMarksObtained(updatedResult.getTotalMarksObtained());
        existingResult.setGrade(updatedResult.getGrade());
        existingResult.setResult(updatedResult.getResult());
        existingResult.setStatus(ResultStatus.REVALUATED);

        studentResultRepository.save(existingResult);
        // Update the RetotallingRequest status
        RetotallingRequest retotallingRequest = retotallingRequestRepository.findByStudentAndExams(existingResult.getStudent(), updatedResult.getExam())
                .orElseThrow(() -> new EntityNotFoundException("No RetotallingRequest found for student and exam"));

        retotallingRequest.setStatus(RetotallingStatus.COMPLETED);
        retotallingRequestRepository.save(retotallingRequest);
    }
    public Map<Institute, List<StudentResult>> getResultsByExamCycleAndExamGroupedByInstitute(ExamCycle examCycle, Exam exam) {
        List<StudentResult> results;
        if (examCycle != null && exam != null) {
            results = studentResultRepository.findByExamCycleAndExam(examCycle, exam);
        } else if (examCycle != null) {
            results = studentResultRepository.findByExamCycle(examCycle);
        } else if (exam != null) {
            results = studentResultRepository.findByExam(exam);
        } else {
            results = studentResultRepository.findAll();
        }

        // Group the results by Institute
        return results.stream()
                .collect(Collectors.groupingBy(result -> result.getStudent().getInstitute()));
    }

    private ExamResultDTO convertToDTO(StudentResult result) {
        ExamResultDTO dto = new ExamResultDTO();

        dto.setInstituteName(result.getStudent().getInstitute().getInstituteName());
        dto.setInstituteId(result.getStudent().getInstitute().getId());
        dto.setStudentName(result.getFirstName() + " " + result.getLastName());
        dto.setCourseName(result.getCourse().getCourseName());
        dto.setExamName(result.getExam().getExamName());
        dto.setInternalMarks(result.getInternalMarksObtained());

        return dto;
    }


}
