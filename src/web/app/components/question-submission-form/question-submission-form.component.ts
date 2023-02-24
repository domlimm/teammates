import { Component, DoCheck, EventEmitter, Input, Output } from '@angular/core';
import { FeedbackQuestionsService } from '../../../services/feedback-questions.service';
import { FeedbackResponsesService } from '../../../services/feedback-responses.service';
import { VisibilityStateMachine } from '../../../services/visibility-state-machine';
import {
  FeedbackConstantSumResponseDetails,
  FeedbackMcqResponseDetails,
  FeedbackMsqResponseDetails,
  FeedbackNumericalScaleResponseDetails,
  FeedbackParticipantType,
  FeedbackQuestionType,
  FeedbackRankOptionsResponseDetails,
  FeedbackResponseDetails,
  FeedbackRubricResponseDetails,
  FeedbackTextQuestionDetails,
  FeedbackTextResponseDetails,
  FeedbackVisibilityType,
  NumberOfEntitiesToGiveFeedbackToSetting,
} from '../../../types/api-output';
import { NUMERICAL_SCALE_ANSWER_NOT_SUBMITTED } from '../../../types/feedback-response-details';
import { VisibilityControl } from '../../../types/visibility-control';
import { SessionView } from '../../pages-session/session-submission-page/session-submission-page.component';
import { CommentRowModel } from '../comment-box/comment-row/comment-row.component';
import { CommentRowMode } from '../comment-box/comment-row/comment-row.mode';
import {
  FeedbackRecipientLabelType,
  FeedbackResponseRecipient,
  FeedbackResponseRecipientSubmissionFormModel,
  QuestionSubmissionFormMode,
  QuestionSubmissionFormModel,
} from './question-submission-form-model';

/**
 * The question submission form for a question.
 */
@Component({
  selector: 'tm-question-submission-form',
  templateUrl: './question-submission-form.component.html',
  styleUrls: ['./question-submission-form.component.scss'],
})
export class QuestionSubmissionFormComponent implements DoCheck {

  // enum
  QuestionSubmissionFormMode: typeof QuestionSubmissionFormMode = QuestionSubmissionFormMode;
  FeedbackQuestionType: typeof FeedbackQuestionType = FeedbackQuestionType;
  FeedbackParticipantType: typeof FeedbackParticipantType = FeedbackParticipantType;
  FeedbackVisibilityType: typeof FeedbackVisibilityType = FeedbackVisibilityType;
  CommentRowMode: typeof CommentRowMode = CommentRowMode;

  isMCQDropDownEnabled: boolean = false;
  isSaved: boolean = false;
  hasResponseChanged: boolean = false;

  @Input()
  formMode: QuestionSubmissionFormMode = QuestionSubmissionFormMode.FIXED_RECIPIENT;

  @Input()
  isFormsDisabled: boolean = false;

  @Input()
  isSubmissionDisabled: boolean = false;

  @Input()
  isSavingResponses: boolean = false;

  @Input()
  set formModel(model: QuestionSubmissionFormModel) {
    this.model = model;
    this.visibilityStateMachine =
        this.feedbackQuestionsService.getNewVisibilityStateMachine(model.giverType, model.recipientType);
    const visibilitySetting: { [TKey in VisibilityControl]: FeedbackVisibilityType[] } = {
      SHOW_RESPONSE: model.showResponsesTo,
      SHOW_GIVER_NAME: model.showGiverNameTo,
      SHOW_RECIPIENT_NAME: model.showRecipientNameTo,
    };
    this.visibilityStateMachine.applyVisibilitySettings(visibilitySetting);
    this.allowedToHaveParticipantComment =
        this.feedbackQuestionsService.isAllowedToHaveParticipantComment(this.model.questionType);
    this.recipientLabelType = this.getSelectionLabelType(model.recipientType);

    // Initialise the hasResponseChanged variable for a recipient when the recipients of
    // the questions is not loaded.
    this.model.recipientList.forEach((recipient: FeedbackResponseRecipient) => {
      if (!this.model.hasResponseChangedForRecipients.has(recipient.recipientIdentifier)) {
        this.model.hasResponseChangedForRecipients.set(recipient.recipientIdentifier, false);
      }
    });
  }

  @Input()
  isQuestionCountOne: boolean = false;

  @Input()
  isSubmitAllClicked: boolean = false;

  allSessionViews = SessionView;

  @Input()
  currentSelectedSessionView: SessionView = SessionView.DEFAULT;

  @Input()
  recipientId: string = '';

  @Output()
  isSubmitAllClickedChange: EventEmitter<boolean> = new EventEmitter<boolean>();

  @Output()
  formModelChange: EventEmitter<QuestionSubmissionFormModel> = new EventEmitter();

  @Output()
  responsesSave: EventEmitter<QuestionSubmissionFormModel> = new EventEmitter();

  model: QuestionSubmissionFormModel = {
    isLoading: false,
    isLoaded: false,
    feedbackQuestionId: '',

    questionNumber: 0,
    questionBrief: '',
    questionDescription: '',

    giverType: FeedbackParticipantType.STUDENTS,
    recipientType: FeedbackParticipantType.STUDENTS,
    recipientList: [],
    recipientSubmissionForms: [],

    questionType: FeedbackQuestionType.TEXT,
    questionDetails: {
      questionText: '',
      questionType: FeedbackQuestionType.TEXT,
    } as FeedbackTextQuestionDetails,

    numberOfEntitiesToGiveFeedbackToSetting: NumberOfEntitiesToGiveFeedbackToSetting.UNLIMITED,
    customNumberOfEntitiesToGiveFeedbackTo: 0,

    showGiverNameTo: [],
    showRecipientNameTo: [],
    showResponsesTo: [],

    hasResponseChangedForRecipients: new Map<string, boolean>(),
  };

  recipientLabelType: FeedbackRecipientLabelType = FeedbackRecipientLabelType.INCLUDE_NAME;
  isSectionTeamShown: boolean = false;

  @Output()
  deleteCommentEvent: EventEmitter<number> = new EventEmitter();

  visibilityStateMachine: VisibilityStateMachine;
  allowedToHaveParticipantComment: boolean = false;
  isEveryRecipientSorted: boolean = false;

  constructor(private feedbackQuestionsService: FeedbackQuestionsService,
              private feedbackResponseService: FeedbackResponsesService) {
    this.visibilityStateMachine =
        this.feedbackQuestionsService.getNewVisibilityStateMachine(
            this.model.giverType, this.model.recipientType);
  }

  get hasSectionTeam(): boolean {
    if (this.formMode === QuestionSubmissionFormMode.FLEXIBLE_RECIPIENT) {
      switch (this.recipientLabelType) {
        case FeedbackRecipientLabelType.INCLUDE_SECTION:
        case FeedbackRecipientLabelType.INCLUDE_TEAM:
          return true;
        default:
          return false;
      }
    }
    return false;
  }

  ngDoCheck(): void {
    if (this.model.isLoaded && !this.isEveryRecipientSorted) {
      this.sortRecipientsByName();
    }

    if (this.model.recipientSubmissionForms.some(
      (response) => response.responseId.length > 0) && !this.isSaved) {
      this.isSaved = true;
    }

    if (this.hasResponseChanged) {
      this.isSaved = false;
    }

    if (this.isSubmitAllClicked) {
      if (this.model.recipientSubmissionForms.some((response) => response.responseId.length > 0)) {
        this.isSaved = true;
      } else if (this.model.recipientSubmissionForms.every((form) => form.responseId.length === 0)) {
        this.isSaved = false;
      }

      this.model.hasResponseChangedForRecipients.forEach((_hasResponseChanged: boolean, recipientId: string) => {
        this.model.hasResponseChangedForRecipients.set(recipientId, false);
      });
    }

    this.model.hasResponseChangedForRecipients.forEach((hasResponseChanged: boolean) => {
      if (hasResponseChanged) {
        this.isSaved = false;
      }
    });
  }

  private compareByName(firstRecipient: FeedbackResponseRecipient,
     secondRecipient: FeedbackResponseRecipient): number {
    return firstRecipient.recipientName.localeCompare(secondRecipient.recipientName);
  }

  private compareBySection(firstRecipient: FeedbackResponseRecipient,
     secondRecipient: FeedbackResponseRecipient): number {

    if (firstRecipient.recipientSection && secondRecipient.recipientSection) {
      return firstRecipient.recipientSection.localeCompare(secondRecipient.recipientSection);
    }

    if (firstRecipient.recipientSection) {
      return -1;
    }

    if (secondRecipient.recipientSection) {
      return 1;
    }

    return 0;
  }

  private compareByTeam(firstRecipient: FeedbackResponseRecipient,
     secondRecipient: FeedbackResponseRecipient): number {

    if (firstRecipient.recipientTeam && secondRecipient.recipientTeam) {
      return firstRecipient.recipientTeam.localeCompare(secondRecipient.recipientTeam);
    }

    if (firstRecipient.recipientTeam) {
      return -1;
    }

    if (secondRecipient.recipientTeam) {
      return 1;
    }

    return 0;
  }

  private updateSubmissionFormIndexes(): void {
    const indexes: Map<String, number> = new Map();
    this.model.recipientList.forEach((recipient: FeedbackResponseRecipient, index: number) => {
      indexes.set(recipient.recipientIdentifier, index + 1);
    });

    this.model.recipientSubmissionForms.sort((firstRecipient: FeedbackResponseRecipientSubmissionFormModel,
      secondRecipient: FeedbackResponseRecipientSubmissionFormModel) => {
      const firstRecipientIndex: number = indexes.get(firstRecipient.recipientIdentifier) || Number.MAX_SAFE_INTEGER;
      const secondRecipientIndex: number = indexes.get(secondRecipient.recipientIdentifier) || Number.MAX_SAFE_INTEGER;

      return firstRecipientIndex - secondRecipientIndex;
    });
    this.isEveryRecipientSorted = true;
  }

  sortRecipientsByName(): void {
    this.model.recipientList.sort(this.compareByName);
    this.updateSubmissionFormIndexes();
  }

  private sortRecipientsBySectionTeam(): void {
    if (this.recipientLabelType === FeedbackRecipientLabelType.INCLUDE_SECTION) {
      this.model.recipientList.sort((firstRecipient, secondRecipient) => {
        return this.compareBySection(firstRecipient, secondRecipient)
          || this.compareByTeam(firstRecipient, secondRecipient);
        });

    } else if (this.recipientLabelType === FeedbackRecipientLabelType.INCLUDE_TEAM) {
      this.model.recipientList.sort(this.compareByTeam);
    }
    this.updateSubmissionFormIndexes();
  }

  /**
   * Tracks submission form for each recipient by the index in the array.
   *
   * @see https://angular.io/api/common/NgForOf#properties
   */
  trackRecipientSubmissionFormByFn(index: number): any {
    return index;
  }

  /**
   * Gets recipient name in {@code FIXED_RECIPIENT} mode.
   */
  getRecipientName(recipientIdentifier: string): string {
    const recipient: FeedbackResponseRecipient | undefined =
        this.model.recipientList.find(
            (r: FeedbackResponseRecipient) => r.recipientIdentifier === recipientIdentifier);
    return recipient ? recipient.recipientName : 'Unknown';
  }

  /**
   * Checks whether the recipient is already selected in {@code FLEXIBLE_RECIPIENT} mode.
   */
  isRecipientSelected(recipient: FeedbackResponseRecipient): boolean {
    return this.model.recipientSubmissionForms.some(
        (recipientSubmissionFormModel: FeedbackResponseRecipientSubmissionFormModel) =>
            recipientSubmissionFormModel.recipientIdentifier === recipient.recipientIdentifier);
  }

  /**
   * Triggers the change of the recipient submission form.
   */
  triggerRecipientSubmissionFormChange(index: number, field: string, data: any): void {
    this.hasResponseChanged = true;
    this.isSubmitAllClickedChange.emit(false);
    this.model.hasResponseChangedForRecipients.set(this.model.recipientList[index].recipientIdentifier, true);

    const recipientSubmissionForms: FeedbackResponseRecipientSubmissionFormModel[] =
        this.model.recipientSubmissionForms.slice();
    recipientSubmissionForms[index] = {
      ...recipientSubmissionForms[index],
      [field]: data,
    };

    this.formModelChange.emit({
      ...this.model,
      recipientSubmissionForms,
    });
  }

  /**
   * Triggers deletion of a participant comment associated with the response.
   */
  triggerDeleteCommentEvent(index: number): void {
    this.deleteCommentEvent.emit(index);
  }

  /**
   * Add new participant comment to response with index.
   */
  addNewParticipantCommentToResponse(index: number): void {
    this.triggerRecipientSubmissionFormChange(index, 'commentByGiver', {
      commentEditFormModel: {
        commentText: '',
      },

      isEditing: true,
    });
  }

  /**
   * Cancel adding new participant comment.
   */
  cancelAddingNewParticipantComment(index: number): void {
    this.triggerRecipientSubmissionFormChange(index, 'commentByGiver', null);
  }

  /**
   * Discards the current editing and restore the original comment.
   */
  discardEditedParticipantComment(index: number): void {
    const commentModel: CommentRowModel | undefined = this.model.recipientSubmissionForms[index].commentByGiver;
    if (!commentModel || !commentModel.originalComment) {
      return;
    }
    this.triggerRecipientSubmissionFormChange(index, 'commentByGiver',
        {
          ...commentModel,
          commentEditFormModel: {
            commentText: commentModel.originalComment.commentText,
          },
          isEditing: false,
        });
  }

  /**
   * Checks whether the response is empty or not.
   */
  isFeedbackResponseDetailsEmpty(responseDetails: FeedbackResponseDetails): boolean {
    return this.feedbackResponseService.isFeedbackResponseDetailsEmpty(
        this.model.questionType, responseDetails);
  }

  /**
   * Updates validity of all responses in a question.
   */
  updateValidity(isValid: boolean): void {
    if (this.model.recipientSubmissionForms.length === 0) { return; }
    const recipientSubmissionForms: FeedbackResponseRecipientSubmissionFormModel[] =
        this.model.recipientSubmissionForms.slice().map(
            (model: FeedbackResponseRecipientSubmissionFormModel) => ({ ...model, isValid }));
    this.formModelChange.emit({
      ...this.model,
      recipientSubmissionForms,
    });
  }

  /**
   * Triggers saving of responses for the specific question.
   */
  saveFeedbackResponses(): void {
    this.isSaved = true;
    this.hasResponseChanged = false;
    this.model.hasResponseChangedForRecipients.forEach(
        (_hasResponseChangedForRecipient: boolean, recipientId: string) => {
        this.model.hasResponseChangedForRecipients.set(recipientId, false);
    });
    this.responsesSave.emit(this.model);
  }

  getSelectionLabelType(recipientType: FeedbackParticipantType): FeedbackRecipientLabelType {
    switch (recipientType) {
      case FeedbackParticipantType.STUDENTS:
      case FeedbackParticipantType.STUDENTS_EXCLUDING_SELF:
        return FeedbackRecipientLabelType.INCLUDE_SECTION;
      case FeedbackParticipantType.STUDENTS_IN_SAME_SECTION:
        return FeedbackRecipientLabelType.INCLUDE_TEAM;
      default:
        return FeedbackRecipientLabelType.INCLUDE_NAME;
    }
  }

  getSelectionOptionLabel(recipient: FeedbackResponseRecipient): string {
    if (!this.isSectionTeamShown) {
      return recipient.recipientName;
    }

    if (recipient.recipientSection && recipient.recipientTeam) {
      return `${recipient.recipientSection} / ${recipient.recipientTeam} | ${recipient.recipientName}`;
    }

    if (recipient.recipientSection) {
      return `${recipient.recipientSection} | ${recipient.recipientName}`;
    }

    if (recipient.recipientTeam) {
      return `${recipient.recipientTeam} | ${recipient.recipientName}`;
    }

    return recipient.recipientName;
  }

  toggleSectionTeam(event: Event): void {
    const checkbox : HTMLInputElement = event.target as HTMLInputElement;
    if (checkbox.checked) {
      this.isSectionTeamShown = true;
      this.sortRecipientsBySectionTeam();
    } else {
      this.isSectionTeamShown = false;
      this.sortRecipientsByName();
    }
  }

  /**
   * Triggers adding a col-12 if MCQ Dropdown is enabled.
   */
  refreshCssForDropdownMCQ(add: boolean): void {
    this.isMCQDropDownEnabled = add;
  }

  /**
   * Checks whether the response of this question has been saved for this recipient.
   */
  isSavedForRecipient(recipientId: string): boolean {
    switch (this.model.questionType) {
      case FeedbackQuestionType.TEXT:
        return this.model.recipientSubmissionForms.reduce(
            (result: boolean, form: FeedbackResponseRecipientSubmissionFormModel) =>
              result || (form.recipientIdentifier === recipientId
                  && !((form.responseDetails as FeedbackTextResponseDetails).answer === ''
                      || this.model.hasResponseChangedForRecipients.get(form.recipientIdentifier))),
            false);
      case FeedbackQuestionType.MCQ:
        return this.model.recipientSubmissionForms.reduce(
            (result: boolean, form: FeedbackResponseRecipientSubmissionFormModel) =>
                result || (form.recipientIdentifier === recipientId
                  && !((form.responseDetails as FeedbackMcqResponseDetails).answer === ''
                  || this.model.hasResponseChangedForRecipients.get(form.recipientIdentifier))),
            false);
      case FeedbackQuestionType.MSQ:
        return this.model.recipientSubmissionForms.reduce(
            (result: boolean, form: FeedbackResponseRecipientSubmissionFormModel) =>
                result || (form.recipientIdentifier === recipientId
                  && !((form.responseDetails as FeedbackMsqResponseDetails).answers.length === 0
                  || this.model.hasResponseChangedForRecipients.get(form.recipientIdentifier))),
            false);
      case FeedbackQuestionType.NUMSCALE:
        return this.model.recipientSubmissionForms.reduce(
            (result: boolean, form: FeedbackResponseRecipientSubmissionFormModel) =>
               result || (form.recipientIdentifier === recipientId
                && !((form.responseDetails as FeedbackNumericalScaleResponseDetails)
                  .answer === NUMERICAL_SCALE_ANSWER_NOT_SUBMITTED
                || this.model.hasResponseChangedForRecipients.get(form.recipientIdentifier))),
            false);
      case FeedbackQuestionType.CONSTSUM_OPTIONS:
        return this.model.recipientSubmissionForms.reduce(
            (result: boolean, form: FeedbackResponseRecipientSubmissionFormModel) =>
                result || (form.recipientIdentifier === recipientId
                  && !((form.responseDetails as FeedbackConstantSumResponseDetails)
                    .answers.length === 0
                  || this.model.hasResponseChangedForRecipients.get(form.recipientIdentifier))),
            false);
      case FeedbackQuestionType.RUBRIC:
        return this.model.recipientSubmissionForms.reduce(
            (result: boolean, form: FeedbackResponseRecipientSubmissionFormModel) =>
                result || (form.recipientIdentifier === recipientId
                  && !((form.responseDetails as FeedbackRubricResponseDetails)
                    .answer.length === 0
                  || this.model.hasResponseChangedForRecipients.get(form.recipientIdentifier))), false);
      case FeedbackQuestionType.RANK_OPTIONS:
        return this.model.recipientSubmissionForms.reduce(
            (result: boolean, form: FeedbackResponseRecipientSubmissionFormModel) =>
                result || (form.recipientIdentifier === recipientId
                  && !((form.responseDetails as FeedbackRankOptionsResponseDetails).answers.length === 0
                  || this.model.hasResponseChangedForRecipients.get(form.recipientIdentifier))), false);
      case FeedbackQuestionType.CONSTSUM_RECIPIENTS:
      case FeedbackQuestionType.CONTRIB:
      case FeedbackQuestionType.RANK_RECIPIENTS:
        return this.isSaved;
      default:
        return false;
    }
  }
}
