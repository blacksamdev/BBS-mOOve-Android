package com.blacksamdev.bbsmoove.service

import android.service.notification.NotificationListenerService

/**
 * Service "coquille vide" : on n'a pas besoin de lire les notifications
 * elles-mêmes, mais MediaSessionManager.getActiveSessions() exige qu'un
 * NotificationListenerService soit déclaré et activé par l'utilisateur
 * pour accepter de renvoyer les contrôleurs média actifs.
 *
 * Déclaration manifest requise (voir AndroidManifest.xml) + activation
 * manuelle dans Réglages > Notifications > Accès aux notifications.
 */
class NotificationListener : NotificationListenerService()
