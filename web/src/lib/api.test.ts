import { ApiError, api } from './api'

vi.mock('./auth', () => ({ authConfig: { mode: 'dev', devUserId: 'test-user' } }))
const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
})
afterEach(() => vi.unstubAllGlobals())

it('sends unique request IDs without changing the AI request body', async () => {
    fetchMock.mockImplementation(async () => new Response('{"name":"Eggs"}'))
    const input = { text: 'Two eggs', images: [] }
    await api.estimateMeal(input)
    await api.estimateMeal(input)
    const requests = fetchMock.mock.calls.map((call) => call[1])
    const ids = requests.map((request) => new Headers(request?.headers).get('X-Request-ID'))
    expect(ids[0]).toMatch(/^[\da-f-]{36}$/)
    expect(ids[1]).not.toBe(ids[0])
    expect(requests[0]?.body).toBe(JSON.stringify(input))
})

it.each([true, false])(
    'attaches a correlation ID to errors (backend header: %s)',
    async (hasHeader) => {
        fetchMock.mockResolvedValue(
            new Response(JSON.stringify({ status: 502, detail: 'AI credits are unavailable.' }), {
                status: 502,
                headers: hasHeader ? { 'X-Request-ID': 'backend-id' } : {},
            }),
        )
        const failure = await api
            .estimateMeal({ text: 'Two eggs', images: [] })
            .catch((error: ApiError) => error)
        expect(failure).toBeInstanceOf(ApiError)
        const sentId = new Headers(fetchMock.mock.calls[0][1]?.headers).get('X-Request-ID')
        expect(failure).toMatchObject({
            problem: { status: 502, detail: 'AI credits are unavailable.' },
            requestId: hasHeader ? 'backend-id' : sentId,
        })
    },
)
