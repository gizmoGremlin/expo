'use strict';

import { Platform } from '@unimodules/core';
import Constants from 'expo-constants';
import * as Notifications from 'expo-notifications';

import * as TestUtils from '../TestUtils';
import { waitFor } from './helpers';

export const name = 'expo-notifications';

export async function test(t) {
  const shouldSkipTestsRequiringPermissions = await TestUtils.shouldSkipTestsRequiringPermissionsAsync();
  const describeWithPermissions = shouldSkipTestsRequiringPermissions ? t.xdescribe : t.describe;

  describeWithPermissions('expo-notifications', () => {
    t.describe('getDevicePushTokenAsync', () => {
      let subscription = null;
      let tokenFromEvent = null;
      let tokenFromMethodCall = null;

      t.beforeAll(() => {
        subscription = Notifications.addPushTokenListener(newEvent => {
          tokenFromEvent = newEvent;
        });
      });

      t.afterAll(() => {
        if (subscription) {
          subscription.remove();
          subscription = null;
        }
      });

      if (Platform.OS === 'android' || Platform.OS === 'ios') {
        t.it('resolves with a string', async () => {
          const devicePushToken = await Notifications.getDevicePushTokenAsync();
          t.expect(typeof devicePushToken.data).toBe('string');
          tokenFromMethodCall = devicePushToken;
        });
      }

      if (Platform.OS === 'web') {
        t.it('resolves with an object', async () => {
          const devicePushToken = await Notifications.getDevicePushTokenAsync();
          t.expect(typeof devicePushToken.data).toBe('object');
          tokenFromMethodCall = devicePushToken;
        });
      }

      t.it('emits an event with token (or not, if getDevicePushTokenAsync failed)', async () => {
        // It would be better to do `if (!tokenFromMethodCall) { pending(); } else { ... }`
        // but `t.pending()` still doesn't work.
        await waitFor(500);
        t.expect(tokenFromEvent).toEqual(tokenFromMethodCall);
      });

      // Not running this test on web since Expo push notification doesn't yet support web.
      const itWithExpoPushToken = ['ios', 'android'].includes(Platform.OS) ? t.it : t.xit;
      itWithExpoPushToken('fetches Expo push token', async () => {
        let experienceId = undefined;
        if (!Constants.manifest) {
          // Absence of manifest means we're running out of managed workflow
          // in bare-expo. @exponent/bare-expo "experience" has been configured
          // to use Apple Push Notification key that will work in bare-expo.
          experienceId = '@exponent/bare-expo';
        }
        const expoPushToken = await Notifications.getExpoPushTokenAsync({
          experienceId,
        });
        t.expect(expoPushToken.type).toBe('expo');
        t.expect(typeof expoPushToken.data).toBe('string');
      });

      // Not running those tests on web since Expo push notification doesn't yet support web.
      const describeWithExpoPushToken = ['ios', 'android'].includes(Platform.OS)
        ? t.describe
        : t.xdescribe;

      describeWithExpoPushToken('when a push notification is sent', () => {
        let receivedEvent = null;
        let subscription = null;

        t.beforeAll(async () => {
          let experienceId = undefined;
          if (!Constants.manifest) {
            // Absence of manifest means we're running out of managed workflow
            // in bare-expo. @exponent/bare-expo "experience" has been configured
            // to use Apple Push Notification key that will work in bare-expo.
            experienceId = '@exponent/bare-expo';
          }
          const pushToken = await Notifications.getExpoPushTokenAsync({
            experienceId,
          });

          subscription = Notifications.addNotificationReceivedListener(event => {
            receivedEvent = event;
          });

          await sendTestPushNotification(pushToken.data);
        });

        t.afterAll(() => {
          subscription.remove();
        });

        t.it('emits a “notification received” event', async () => {
          let iterations = 0;
          while (iterations < 5) {
            iterations += 1;
            if (receivedEvent) {
              break;
            }
            await waitFor(1000);
          }
          t.expect(receivedEvent).not.toBeNull();
        });
      });
    });
  });
}

// In this test app we contact the Expo push service directly. You *never*
// should do this in a real app. You should always store the push tokens on your
// own server or use the local notification API if you want to notify this user.
const PUSH_ENDPOINT = 'https://expo.io/--/api/v2/push/send';

async function sendTestPushNotification(expoPushToken, notificationOverrides) {
  // POST the token to the Expo push server
  const response = await fetch(PUSH_ENDPOINT, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify([
      {
        to: expoPushToken,
        title: 'Hello from Expo server!',
        ...notificationOverrides,
      },
    ]),
  });

  const result = await response.json();
  if (result.errors) {
    for (const error of result.errors) {
      console.warn(`API error sending push notification:`, error);
    }
    throw new Error('API error has occurred.');
  }

  const receipts = result.data;
  if (receipts) {
    const receipt = receipts[0];
    if (receipt.status === 'error') {
      if (receipt.details) {
        console.warn(
          `Expo push service reported an error sending a notification: ${receipt.details.error}`
        );
      }
      if (receipt.__debug) {
        console.warn(receipt.__debug);
      }
      throw new Error(`API error has occurred: ${receipt.details.error}`);
    }
  }
}
