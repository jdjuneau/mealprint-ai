const functions = require('firebase-functions');
const admin = require('firebase-admin');
if (!admin.apps.length) { admin.initializeApp(); }
const processBriefTaskHttp = require('./process-brief-task-http');
exports.processBriefTask = processBriefTaskHttp.processBriefTask;
