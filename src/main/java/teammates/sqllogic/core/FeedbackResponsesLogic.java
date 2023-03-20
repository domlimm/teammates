package teammates.sqllogic.core;

import java.util.List;
import java.util.UUID;

import teammates.common.datatransfer.FeedbackParticipantType;
import teammates.storage.sqlapi.FeedbackResponsesDb;
import teammates.storage.sqlentity.FeedbackQuestion;
import teammates.storage.sqlentity.FeedbackResponse;

/**
 * Handles operations related to feedback sessions.
 *
 * @see FeedbackResponse
 * @see FeedbackResponsesDb
 */
public final class FeedbackResponsesLogic {

    private static final FeedbackResponsesLogic instance = new FeedbackResponsesLogic();

    private FeedbackResponsesDb frDb;

    private FeedbackResponseCommentsLogic feedbackResponseCommentsLogic;

    private FeedbackResponsesLogic() {
        // prevent initialization
    }

    public static FeedbackResponsesLogic inst() {
        return instance;
    }

    /**
     * Initialize dependencies for {@code FeedbackResponsesLogic}.
     */
    void initLogicDependencies(FeedbackResponsesDb frDb,
            FeedbackResponseCommentsLogic feedbackResponseCommentsLogic) {
        this.frDb = frDb;
        this.feedbackResponseCommentsLogic = feedbackResponseCommentsLogic;
    }

    /**
     * Returns true if the responses of the question are visible to students.
     */
    public boolean isResponseOfFeedbackQuestionVisibleToStudent(FeedbackQuestion question) {
        if (question.isResponseVisibleTo(FeedbackParticipantType.STUDENTS)) {
            return true;
        }
        boolean isStudentRecipientType =
                   question.getRecipientType().equals(FeedbackParticipantType.STUDENTS)
                || question.getRecipientType().equals(FeedbackParticipantType.STUDENTS_EXCLUDING_SELF)
                || question.getRecipientType().equals(FeedbackParticipantType.STUDENTS_IN_SAME_SECTION)
                || question.getRecipientType().equals(FeedbackParticipantType.OWN_TEAM_MEMBERS)
                || question.getRecipientType().equals(FeedbackParticipantType.OWN_TEAM_MEMBERS_INCLUDING_SELF)
                || question.getRecipientType().equals(FeedbackParticipantType.GIVER)
                   && question.getGiverType().equals(FeedbackParticipantType.STUDENTS);

        if ((isStudentRecipientType || question.getRecipientType().isTeam())
                && question.isResponseVisibleTo(FeedbackParticipantType.RECEIVER)) {
            return true;
        }
        if (question.getGiverType() == FeedbackParticipantType.TEAMS
                || question.isResponseVisibleTo(FeedbackParticipantType.OWN_TEAM_MEMBERS)) {
            return true;
        }
        return question.isResponseVisibleTo(FeedbackParticipantType.RECEIVER_TEAM_MEMBERS);
    }

    /**
     * Returns true if the responses of the question are visible to instructors.
     */
    public boolean isResponseOfFeedbackQuestionVisibleToInstructor(FeedbackQuestion question) {
        return question.isResponseVisibleTo(FeedbackParticipantType.INSTRUCTORS);
    }

    /**
     * Deletes all feedback responses involved an entity, cascade its associated comments.
     */
    public void deleteFeedbackResponsesInvolvedEntityOfCourseCascade(String courseId, String entityEmail) {
        // delete responses from the entity
        List<FeedbackResponse> responsesFromStudent =
                getFeedbackResponsesFromGiverForCourse(courseId, entityEmail);
        for (FeedbackResponse response : responsesFromStudent) {
            deleteFeedbackResponseCascade(response.getId());
        }

        // delete responses to the entity
        List<FeedbackResponse> responsesToStudent =
                getFeedbackResponsesForReceiverForCourse(courseId, entityEmail);
        for (FeedbackResponse response : responsesToStudent) {
            deleteFeedbackResponseCascade(response.getId());
        }
    }

    /**
     * Gets all responses given by a user for a course.
     */
    public List<FeedbackResponse> getFeedbackResponsesFromGiverForCourse(
            String courseId, String giver) {
        assert courseId != null;
        assert giver != null;

        return frDb.getFeedbackResponsesFromGiverForCourse(courseId, giver);
    }

    /**
     * Gets all responses received by a user for a course.
     */
    public List<FeedbackResponse> getFeedbackResponsesForReceiverForCourse(
            String courseId, String receiver) {
        assert courseId != null;
        assert receiver != null;

        return frDb.getFeedbackResponsesForReceiverForCourse(courseId, receiver);
    }

    /**
     * Deletes a feedback response, cascade its associated comments.
     */
    public void deleteFeedbackResponseCascade(UUID responseId) {
        FeedbackResponse feedbackResponseToDelete = frDb.getFeedbackResponse(responseId);

        frDb.deleteFeedbackResponse(feedbackResponseToDelete);
    }
}
