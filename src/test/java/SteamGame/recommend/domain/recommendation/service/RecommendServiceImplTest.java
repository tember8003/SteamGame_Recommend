package SteamGame.recommend.domain.recommendation.service;

class RecommendServiceImplTest {
    /* 잠시 테스트 주석처리
    @Mock
    CooccurrenceService cooccurrenceService;
    @Mock
    GameFinderService gameFinderService;

    @InjectMocks
    private RecommendServiceImpl recommendService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void whenOptimalPairExists_shouldUseThatPair() {
        // given
        TagPairKey pairKey = new TagPairKey("A","B");
        when(cooccurrenceService.findOptimalPairKey(anyList(), anyInt()))
                .thenReturn(Optional.of(pairKey));

        SteamDTO.SteamApp fakeApp = new SteamDTO.SteamApp();
        when(gameFinderService.findNonDuplicate(
                eq(new String[]{"A","B"}), anyInt(), anyBoolean(), any()))
                .thenReturn(fakeApp);

        // when
        SteamDTO.SteamApp result = recommendService.recommendWithCooccurrence(List.of("A","B","C"));

        // then
        assertSame(fakeApp, result);
        verify(gameFinderService).findNonDuplicate(
                new String[]{"A","B"},
                1000,
                true, null);
    }

    @Test
    void whenNoOptimalPair_shouldTrySingleTagsInOrder() {
        // given
        when(cooccurrenceService.findOptimalPairKey(anyList(), anyInt()))
                .thenReturn(Optional.empty());

        // 첫 태그 실패
        when(gameFinderService.findNonDuplicate(
                eq(new String[]{"X"}), anyInt(), anyBoolean(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "테스트용 예외"));

        // 두 번째 태그 성공
        SteamDTO.SteamApp secondApp = new SteamDTO.SteamApp();
        when(gameFinderService.findNonDuplicate(
                eq(new String[]{"Y"}), anyInt(), anyBoolean(), any()))
                .thenReturn(secondApp);

        // when
        SteamDTO.SteamApp result = recommendService.recommendWithCooccurrence(List.of("X","Y"));

        // then
        assertSame(secondApp, result);
    }
     */
}