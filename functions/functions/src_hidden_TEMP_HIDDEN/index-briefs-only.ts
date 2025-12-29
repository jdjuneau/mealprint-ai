/**
 * MINIMAL INDEX FOR BRIEF FUNCTIONS ONLY
 * This file exports ONLY brief-related functions to avoid Firebase timeout
 * Used for isolated deployment
 */

// Import brief functions
import { sendMorningBriefs, sendAfternoonBriefs, sendEveningBriefs, triggerMorningBrief, onNewUserCreated } from './scheduledBriefs';
import { processBriefTask } from './briefTaskQueue';

// Export only brief functions
export { 
  sendMorningBriefs, 
  sendAfternoonBriefs, 
  sendEveningBriefs, 
  processBriefTask, 
  triggerMorningBrief, 
  onNewUserCreated 
};
