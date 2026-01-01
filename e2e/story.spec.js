const { test, expect } = require('@playwright/test');

const pixelBase64 =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIW2P4//8/AwAI/AL+N1t8WQAAAABJRU5ErkJggg==';

const startResponse = {
  assistant: {
    storyText: 'Die Kerzen in der Großen Halle flackern, als du dich erhebst. Was tust du?',
    suggestedActions: ['Zur Tür schleichen', 'Lumos wirken'],
    newItems: [
      {
        name: 'Silberner Schlüssel',
        description: 'Kalt, schwer, mit einem eingravierten Raben',
        foundAt: '2026-01-01T10:07:12Z'
      }
    ],
    adventure: {
      title: 'Die Tür im Nordturm',
      completed: false,
      summary: null,
      completedAt: null
    },
    image: {
      mimeType: 'image/png',
      base64: pixelBase64,
      prompt: 'Große Halle bei Kerzenlicht'
    }
  }
};

const completedResponse = {
  assistant: {
    storyText: 'Du schleichst zur Tür und hörst das Flüstern dahinter. Das Abenteuer endet.',
    suggestedActions: [],
    newItems: [],
    adventure: {
      title: 'Die Tür im Nordturm',
      completed: true,
      summary: 'Du fandest den Schlüssel und entkamst dem Flüstern.',
      completedAt: '2026-01-01T10:12:00Z'
    },
    image: {
      mimeType: 'image/png',
      base64: pixelBase64,
      prompt: 'Hogwarts-Tür im Schatten'
    }
  }
};

function lower(value) {
  return typeof value === 'string' ? value.toLowerCase() : '';
}

test('plays a short adventure end-to-end with mocked story responses', async ({ page }) => {
  await page.route('**/api/story/stream', async (route) => {
    const data = route.request().postDataJSON();
    const action = lower(data && data.action);

    if (action === 'start') {
      await route.fulfill({
        status: 200,
        headers: { 'content-type': 'text/event-stream' },
        body: buildSseResponse(startResponse)
      });
      return;
    }

    if (action === 'zur tür schleichen') {
      await route.fulfill({
        status: 200,
        headers: { 'content-type': 'text/event-stream' },
        body: buildSseResponse(completedResponse)
      });
      return;
    }

    await route.fulfill({
      status: 200,
      headers: { 'content-type': 'text/event-stream' },
      body: [
        'event: error',
        `data: ${JSON.stringify({ error: { code: 'UNEXPECTED_ACTION', message: \`Unexpected action: ${action}\` } })}`,
        '',
        ''
      ].join('\n')
    });
  });

  await page.goto('/');

  await page.getByTestId('player-name').fill('Hermine');
  await page.getByTestId('player-house').fill('Gryffindor');
  await page.getByTestId('start-adventure').click();

  await expect(page.getByTestId('story-feed')).toContainText('Du: start');
  await expect(page.getByTestId('assistant-story')).toContainText('Die Kerzen in der Großen Halle flackern');
  await expect(page.getByTestId('assistant-image')).toHaveAttribute('src', /data:image\/png;base64/);
  await expect(page.getByTestId('inventory-panel')).toContainText('Silberner Schlüssel');

  await page.getByRole('button', { name: 'Zur Tür schleichen' }).click();

  await expect(page.getByTestId('notice-message')).toContainText('Abenteuer abgeschlossen: Die Tür im Nordturm');
  await expect(page.getByTestId('history-panel')).toContainText('Die Tür im Nordturm');
  await expect(page.getByTestId('stats-adventures')).toContainText('1');
});

function buildSseResponse(response) {
  const story = response.assistant.storyText || '';
  const midpoint = Math.max(1, Math.floor(story.length / 2));
  const first = story.slice(0, midpoint);
  const second = story.slice(midpoint);

  return [
    'event: delta',
    `data: ${JSON.stringify({ text: first })}`,
    '',
    'event: delta',
    `data: ${JSON.stringify({ text: second })}`,
    '',
    'event: final',
    `data: ${JSON.stringify(response)}`,
    '',
    ''
  ].join('\n');
}
