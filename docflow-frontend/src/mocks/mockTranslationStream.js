const sampleTranslations = [
  `# Page 1

This is the translated content for page 1.

Lorem ipsum dolor sit amet, consectetur adipiscing elit.
Vivamus vulputate urna sed magna volutpat feugiat.
`,

  `# Page 2

This is the translated content for page 2.

Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium.
`,

  `# Page 3

This is the translated content for page 3.

At vero eos et accusamus et iusto odio dignissimos ducimus.
`,

  `# Page 4

This is the translated content for page 4.

Neque porro quisquam est qui dolorem ipsum quia dolor sit amet.
`,

  `# Page 5

This is the translated content for page 5.

The translation has completed successfully.
`,
];

export function startMockTranslation({
  totalPages = 5,
  delay = 2000,
  onPage,
  onComplete,
}) {
  let page = 0;

  const timer = setInterval(() => {
    if (page >= totalPages) {
      clearInterval(timer);
      onComplete?.();
      return;
    }

    onPage?.({
      pageNo: page + 1,
      translation: sampleTranslations[page],
    });

    page++;
  }, delay);

  return () => clearInterval(timer);
}