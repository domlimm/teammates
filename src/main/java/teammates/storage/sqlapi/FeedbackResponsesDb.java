package teammates.storage.sqlapi;

import static teammates.common.util.Const.ERROR_CREATE_ENTITY_ALREADY_EXISTS;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import teammates.common.exception.EntityAlreadyExistsException;
import teammates.common.exception.InvalidParametersException;
import teammates.common.util.HibernateUtil;
import teammates.storage.sqlentity.FeedbackQuestion;
import teammates.storage.sqlentity.FeedbackResponse;
import teammates.storage.sqlentity.FeedbackSession;

/**
 * Handles CRUD operations for feedbackResponses.
 *
 * @see FeedbackResponse
 */
public final class FeedbackResponsesDb extends EntitiesDb {

    private static final FeedbackResponsesDb instance = new FeedbackResponsesDb();

    private FeedbackResponsesDb() {
        // prevent initialization
    }

    public static FeedbackResponsesDb inst() {
        return instance;
    }

    /**
     * Gets a feedbackResponse or null if it does not exist.
     */
    public FeedbackResponse getFeedbackResponse(UUID frId) {
        assert frId != null;

        return HibernateUtil.get(FeedbackResponse.class, frId);
    }

    /**
     * Gets all responses given by a user in a course.
     */
    public List<FeedbackResponse> getFeedbackResponsesFromGiverForCourse(
            String courseId, String giver) {
        CriteriaBuilder cb = HibernateUtil.getCriteriaBuilder();
        CriteriaQuery<FeedbackResponse> cr = cb.createQuery(FeedbackResponse.class);
        Root<FeedbackResponse> frRoot = cr.from(FeedbackResponse.class);
        Join<FeedbackResponse, FeedbackQuestion> fqJoin = frRoot.join("feedbackQuestion");
        Join<FeedbackQuestion, FeedbackSession> fsJoin = fqJoin.join("feedbackSession");

        cr.select(frRoot)
                .where(cb.and(
                    cb.equal(fsJoin.get("courseId"), courseId),
                    cb.equal(frRoot.get("giver"), giver)));

        return HibernateUtil.createQuery(cr).getResultList();
    }

    /**
     * Gets all responses given to a user in a course.
     */
    public List<FeedbackResponse> getFeedbackResponsesForReceiverForCourse(String courseId, String receiver) {
        CriteriaBuilder cb = HibernateUtil.getCriteriaBuilder();
        CriteriaQuery<FeedbackResponse> cr = cb.createQuery(FeedbackResponse.class);
        Root<FeedbackResponse> frRoot = cr.from(FeedbackResponse.class);
        Join<FeedbackResponse, FeedbackQuestion> fqJoin = frRoot.join("feedbackQuestion");
        Join<FeedbackQuestion, FeedbackSession> fsJoin = fqJoin.join("feedbackSession");

        cr.select(frRoot)
                .where(cb.and(
                    cb.equal(fsJoin.get("courseId"), courseId),
                    cb.equal(frRoot.get("receiver"), receiver)));

        return HibernateUtil.createQuery(cr).getResultList();
    }

    /**
     * Creates a feedbackResponse.
     */
    public FeedbackResponse createFeedbackResponse(FeedbackResponse feedbackResponse)
            throws InvalidParametersException, EntityAlreadyExistsException {
        assert feedbackResponse != null;

        if (!feedbackResponse.isValid()) {
            throw new InvalidParametersException(feedbackResponse.getInvalidityInfo());
        }

        if (getFeedbackResponse(feedbackResponse.getId()) != null) {
            throw new EntityAlreadyExistsException(
                    String.format(ERROR_CREATE_ENTITY_ALREADY_EXISTS, feedbackResponse.toString()));
        }

        persist(feedbackResponse);
        return feedbackResponse;
    }

    /**
     * Deletes a feedbackResponse.
     */
    public void deleteFeedbackResponse(FeedbackResponse feedbackResponse) {
        if (feedbackResponse != null) {
            delete(feedbackResponse);
        }
    }

}
