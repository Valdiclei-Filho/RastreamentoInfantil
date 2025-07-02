/**
 * Import function triggers from their respective submodules:
 *
 * import {onCall} from "firebase-functions/v2/https";
 * import {onDocumentWritten} from "firebase-functions/v2/firestore";
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

import {onRequest} from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";

// Start writing functions
// https://firebase.google.com/docs/functions/typescript

// export const helloWorld = onRequest((request, response) => {
//   logger.info("Hello logs!", {structuredData: true});
//   response.send("Hello from Firebase!");
// });

admin.initializeApp();

export const sendEventNotification = onRequest(async (req, res) => {
  try {
    const { dependentId, responsibleId, title, body, data } = req.body;
    if (!dependentId || !responsibleId || !title || !body) {
      res.status(400).send("Campos obrigatórios ausentes.");
      return;
    }

    // Buscar tokens FCM dos usuários
    const usersRef = admin.firestore().collection("users");
    const dependentSnap = await usersRef.doc(dependentId).get();
    const responsibleSnap = await usersRef.doc(responsibleId).get();
    const dependentToken = dependentSnap.get("fcmToken");
    const responsibleToken = responsibleSnap.get("fcmToken");

    const tokens = [dependentToken, responsibleToken].filter((t): t is string => typeof t === 'string' && !!t);
    if (tokens.length === 0) {
      res.status(404).send("Nenhum token FCM encontrado.");
      return;
    }

    // Enviar notificação para múltiplos dispositivos
    const multicastMessage = {
      tokens,
      notification: {
        title,
        body,
      },
      data: data || {},
    };
    const response = await admin.messaging().sendEachForMulticast(multicastMessage);
    res.status(200).send({ success: true, response });
  } catch (error) {
    logger.error("Erro ao enviar notificação:", error);
    res.status(500).send({ success: false, error: (error as Error).message });
  }
});
