module Update exposing (update)

import Api
import Model
import Msg exposing (Msg(..))
import String

update : (Model.GameState -> Cmd Msg) -> Msg -> Model.GameState -> ( Model.GameState, Cmd Msg )
update save msg state =
    case msg of
        UpdateName name ->
            let
                currentPlayer =
                    state.player

                nextPlayer =
                    { currentPlayer | name = name }

                next =
                    { state | player = nextPlayer }
            in
            ( next, save next )

        UpdateHouse house ->
            let
                currentPlayer =
                    state.player

                nextPlayer =
                    { currentPlayer | houseName = house }

                next =
                    { state | player = nextPlayer }
            in
            ( next, save next )

        UpdateActionInput value ->
            ( { state | actionInput = value }, Cmd.none )

        StartAdventure ->
            if not (Model.isProfileComplete state.player) then
                setError save "Bitte gib deinen Namen und dein Haus an." state

            else
                case state.currentAdventure of
                    Just _ ->
                        setError save "Du bist bereits in einem Abenteuer." state

                    Nothing ->
                        beginAdventure save state

        SendAction ->
            let
                trimmed =
                    String.trim state.actionInput
            in
            if trimmed == "" then
                ( state, Cmd.none )

            else
                sendAction save trimmed state

        UseSuggestedAction action ->
            sendAction save action state

        GotStoryResponse result ->
            case result of
                Ok response ->
                    applyStoryResponse save response state

                Err error ->
                    let
                        next =
                            { state
                                | isLoading = False
                                , error = Just (Api.errorToString error)
                            }
                    in
                    ( next, save next )

        ResetState ->
            let
                next =
                    Model.defaultState
            in
            ( next, save next )


beginAdventure : (Model.GameState -> Cmd Msg) -> Model.GameState -> ( Model.GameState, Cmd Msg )
beginAdventure save state =
    let
        adventure =
            { title = Nothing
            , startedAt = ""
            , turns = []
            }

        next =
            { state | currentAdventure = Just adventure }
    in
    sendAction save "start" next


sendAction : (Model.GameState -> Cmd Msg) -> String -> Model.GameState -> ( Model.GameState, Cmd Msg )
sendAction save action state =
    case state.currentAdventure of
        Nothing ->
            setError save "Starte zuerst ein Abenteuer." state

        Just adventure ->
            let
                newTurn =
                    { userAction = action, assistant = Nothing }

                updatedAdventure =
                    { adventure | turns = adventure.turns ++ [ newTurn ] }

                next =
                    { state
                        | currentAdventure = Just updatedAdventure
                        , actionInput = ""
                        , isLoading = True
                        , error = Nothing
                    }
            in
            ( next
            , Cmd.batch
                [ save next
                , Api.sendStory state action GotStoryResponse
                ]
            )


applyStoryResponse : (Model.GameState -> Cmd Msg) -> Api.StoryResponse -> Model.GameState -> ( Model.GameState, Cmd Msg )
applyStoryResponse save response state =
    case state.currentAdventure of
        Nothing ->
            let
                next =
                    { state | isLoading = False }
            in
            ( next, save next )

        Just adventure ->
            let
                assistantTurn =
                    { storyText = response.assistant.storyText
                    , suggestedActions = response.assistant.suggestedActions
                    }

                updatedAdventure =
                    adventure
                        |> updateLastTurn assistantTurn
                        |> updateAdventureTitle response.assistant.adventure.title

                next =
                    { state
                        | currentAdventure = Just updatedAdventure
                        , isLoading = False
                        , error = Nothing
                    }
            in
            ( next, save next )


updateLastTurn : Model.AssistantTurn -> Model.Adventure -> Model.Adventure
updateLastTurn assistant adventure =
    case List.reverse adventure.turns of
        [] ->
            adventure

        lastTurn :: rest ->
            let
                updatedTurn =
                    { lastTurn | assistant = Just assistant }
            in
            { adventure | turns = List.reverse (updatedTurn :: rest) }


updateAdventureTitle : Maybe String -> Model.Adventure -> Model.Adventure
updateAdventureTitle maybeTitle adventure =
    case ( adventure.title, maybeTitle ) of
        ( Nothing, Just title ) ->
            { adventure | title = Just title }

        _ ->
            adventure


setError : (Model.GameState -> Cmd Msg) -> String -> Model.GameState -> ( Model.GameState, Cmd Msg )
setError save message state =
    let
        next =
            { state | error = Just message }
    in
    ( next, save next )
