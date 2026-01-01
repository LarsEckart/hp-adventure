module Update exposing (update)

import Browser.Dom as Dom
import Api
import Json.Decode as Decode
import Json.Encode as Encode
import Model
import Msg exposing (Msg(..))
import Maybe
import Set
import String
import Task
import Time
import Util

update : (Model.GameState -> Cmd Msg) -> (Encode.Value -> Cmd Msg) -> Msg -> Model.GameState -> ( Model.GameState, Cmd Msg )
update save startStream msg state =
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

        OnlineStatusChanged isOnline ->
            let
                next =
                    { state | isOnline = isOnline }
            in
            ( next, Cmd.none )

        StartAdventure ->
            startAdventure save startStream state

        GotStartTime startedAt ->
            beginAdventure save startStream startedAt state

        SendAction ->
            handleAction save startStream state.actionInput state

        UseSuggestedAction action ->
            handleAction save startStream action state

        GotStoryResponse result ->
            case result of
                Ok response ->
                    applyStoryResponse save response state

                Err error ->
                    let
                        next =
                            dropPendingTurn state
                                |> (\updated ->
                                        { updated
                                            | isLoading = False
                                            , error = Just (Api.errorToString error)
                                            , pendingAbandon = False
                                        }
                                   )
                    in
                    ( next, save next )

        GotStoryStreamEvent payload ->
            handleStreamEvent save payload state

        ToggleInventory ->
            ( { state | showInventory = not state.showInventory }, Cmd.none )

        ToggleHistory ->
            ( { state | showHistory = not state.showHistory }, Cmd.none )

        RequestAbandon ->
            requestAbandon save state

        ConfirmAbandon ->
            confirmAbandon save state

        CancelAbandon ->
            cancelAbandon save state

        DismissNotice ->
            ( { state | notice = Nothing }, Cmd.none )

        ResetState ->
            let
                baseState =
                    Model.defaultState

                next =
                    { baseState | isOnline = state.isOnline }
            in
            ( next, save next )

        ScrolledToBottom _ ->
            ( state, Cmd.none )


startAdventure : (Model.GameState -> Cmd Msg) -> (Encode.Value -> Cmd Msg) -> Model.GameState -> ( Model.GameState, Cmd Msg )
startAdventure save startStream state =
    if not state.isOnline then
        setError save offlineMessage state

    else if not (Model.isProfileComplete state.player) then
        setError save "Bitte gib deinen Namen und dein Haus an." state

    else
        case state.currentAdventure of
            Just _ ->
                setError save "Du bist bereits in einem Abenteuer." state

            Nothing ->
                let
                    next =
                        { state | error = Nothing, notice = Nothing }
                in
                ( next, Task.perform GotStartTime Time.now )


beginAdventure : (Model.GameState -> Cmd Msg) -> (Encode.Value -> Cmd Msg) -> Time.Posix -> Model.GameState -> ( Model.GameState, Cmd Msg )
beginAdventure save startStream startedAt state =
    let
        startedAtIso =
            Util.posixToIso startedAt

        updatedPlayer =
            ensureStarterItems startedAtIso state.player

        adventure =
            { title = Nothing
            , startedAt = startedAtIso
            , turns = []
            }

        next =
            { state | currentAdventure = Just adventure, notice = Nothing, player = updatedPlayer }
    in
    sendAction save startStream "start" next


handleAction : (Model.GameState -> Cmd Msg) -> (Encode.Value -> Cmd Msg) -> String -> Model.GameState -> ( Model.GameState, Cmd Msg )
handleAction save startStream rawAction state =
    let
        trimmed =
            String.trim rawAction

        command =
            String.toLower trimmed
    in
    if trimmed == "" then
        ( state, Cmd.none )

    else if command == "inventar" then
        let
            next =
                { state | showInventory = True, actionInput = "", notice = Just "Inventar geöffnet." }
        in
        ( next, save next )

    else if command == "geschichte" then
        let
            next =
                { state | showHistory = True, actionInput = "", notice = Just "Abenteuerchronik geöffnet." }
        in
        ( next, save next )

    else if command == "aufgeben" then
        requestAbandon save state

    else if command == "start" then
        startAdventure save startStream state

    else
        sendAction save startStream trimmed state


sendAction : (Model.GameState -> Cmd Msg) -> (Encode.Value -> Cmd Msg) -> String -> Model.GameState -> ( Model.GameState, Cmd Msg )
sendAction save startStream action state =
    if not state.isOnline then
        setError save offlineMessage state

    else
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
                            , notice = Nothing
                            , pendingAbandon = False
                        }
                in
                ( next
                , Cmd.batch
                    [ save next
                    , startStream (Api.encodeStoryRequest state action)
                    , scrollToBottom
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
                    , newItems = response.assistant.newItems
                    , adventureCompleted = response.assistant.adventure.completed
                    , image = response.assistant.image
                    }

                updatedAdventure =
                    adventure
                        |> updateLastTurn assistantTurn
                        |> updateAdventureTitle response.assistant.adventure.title

                updatedPlayer =
                    state.player
                        |> addItems response.assistant.newItems
                        |> incrementTurns

                ( finalPlayer, finalAdventure, notice ) =
                    if response.assistant.adventure.completed then
                        let
                            title =
                                case response.assistant.adventure.title of
                                    Just value ->
                                        value

                                    Nothing ->
                                        case updatedAdventure.title of
                                            Just existing ->
                                                existing

                                            Nothing ->
                                                "Unbenanntes Abenteuer"

                            summary =
                                Maybe.withDefault "" response.assistant.adventure.summary

                            completedAt =
                                Maybe.withDefault "" response.assistant.adventure.completedAt

                            completedAdventure =
                                { title = title
                                , summary = summary
                                , completedAt = completedAt
                                }

                            updatedStats =
                                { adventuresCompleted = updatedPlayer.stats.adventuresCompleted + 1
                                , totalTurns = updatedPlayer.stats.totalTurns
                                }
                        in
                        ( { updatedPlayer
                            | completedAdventures = completedAdventure :: updatedPlayer.completedAdventures
                            , stats = updatedStats
                          }
                        , Nothing
                        , Just ("Abenteuer abgeschlossen: " ++ title)
                        )

                    else
                        ( updatedPlayer
                        , Just updatedAdventure
                        , newItemNotice response.assistant.newItems
                        )

                next =
                    { state
                        | currentAdventure = finalAdventure
                        , isLoading = False
                        , error = Nothing
                        , notice = notice
                        , player = finalPlayer
                        , pendingAbandon = False
                    }
            in
            ( next, Cmd.batch [ save next, scrollToBottom ] )


handleStreamEvent : (Model.GameState -> Cmd Msg) -> Decode.Value -> Model.GameState -> ( Model.GameState, Cmd Msg )
handleStreamEvent save payload state =
    case Decode.decodeValue Api.decodeStreamEvent payload of
        Ok event ->
            case event of
                Api.StreamDelta delta ->
                    ( applyStreamDelta delta state, Cmd.none )

                Api.StreamFinal response ->
                    applyStoryResponse save response state

                Api.StreamError message ->
                    let
                        next =
                            dropPendingTurn state
                                |> (\updated ->
                                        { updated | isLoading = False, error = Just message, pendingAbandon = False }
                                   )
                    in
                    ( next, save next )

        Err _ ->
            ( state, Cmd.none )


applyStreamDelta : String -> Model.GameState -> Model.GameState
applyStreamDelta delta state =
    case state.currentAdventure of
        Nothing ->
            state

        Just adventure ->
            let
                updatedAdventure =
                    updateLastTurnWithDelta delta adventure
            in
            { state | currentAdventure = Just updatedAdventure }


dropPendingTurn : Model.GameState -> Model.GameState
dropPendingTurn state =
    case state.currentAdventure of
        Nothing ->
            state

        Just adventure ->
            case List.reverse adventure.turns of
                [] ->
                    state

                lastTurn :: rest ->
                    case lastTurn.assistant of
                        Nothing ->
                            let
                                updatedAdventure =
                                    { adventure | turns = List.reverse rest }
                            in
                            { state | currentAdventure = Just updatedAdventure }

                        Just _ ->
                            state


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


updateLastTurnWithDelta : String -> Model.Adventure -> Model.Adventure
updateLastTurnWithDelta delta adventure =
    case List.reverse adventure.turns of
        [] ->
            adventure

        lastTurn :: rest ->
            let
                updatedAssistant =
                    case lastTurn.assistant of
                        Nothing ->
                            { storyText = delta
                            , suggestedActions = []
                            , newItems = []
                            , adventureCompleted = False
                            , image = Nothing
                            }

                        Just assistant ->
                            { assistant | storyText = assistant.storyText ++ delta }

                updatedTurn =
                    { lastTurn | assistant = Just updatedAssistant }
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


requestAbandon : (Model.GameState -> Cmd Msg) -> Model.GameState -> ( Model.GameState, Cmd Msg )
requestAbandon save state =
    case state.currentAdventure of
        Nothing ->
            setError save "Es gibt kein Abenteuer zum Aufgeben." state

        Just _ ->
            let
                next =
                    { state
                        | pendingAbandon = True
                        , notice = Just "Möchtest du das Abenteuer wirklich aufgeben?"
                        , actionInput = ""
                    }
            in
            ( next, save next )


confirmAbandon : (Model.GameState -> Cmd Msg) -> Model.GameState -> ( Model.GameState, Cmd Msg )
confirmAbandon save state =
    let
        next =
            { state
                | currentAdventure = Nothing
                , pendingAbandon = False
                , notice = Just "Abenteuer aufgegeben."
                , isLoading = False
            }
    in
    ( next, save next )


cancelAbandon : (Model.GameState -> Cmd Msg) -> Model.GameState -> ( Model.GameState, Cmd Msg )
cancelAbandon save state =
    let
        next =
            { state | pendingAbandon = False, notice = Nothing }
    in
    ( next, save next )


addItems : List Model.Item -> Model.Player -> Model.Player
addItems newItems player =
    let
        existingNames =
            player.inventory
                |> List.map (\item -> String.toLower item.name)
                |> Set.fromList

        uniqueNewItems =
            newItems
                |> List.filter (\item -> not (Set.member (String.toLower item.name) existingNames))
    in
    { player | inventory = player.inventory ++ uniqueNewItems }


ensureStarterItems : String -> Model.Player -> Model.Player
ensureStarterItems startedAtIso player =
    let
        hasWand =
            player.inventory
                |> List.any (\item -> String.toLower item.name == "zauberstab")

        wandItem =
            { name = "Zauberstab"
            , description = "Ein einfacher Schulzauberstab."
            , foundAt = startedAtIso
            }

        nextInventory =
            if hasWand then
                player.inventory
            else
                wandItem :: player.inventory
    in
    { player | inventory = nextInventory }


incrementTurns : Model.Player -> Model.Player
incrementTurns player =
    let
        currentStats =
            player.stats
    in
    { player | stats = { currentStats | totalTurns = currentStats.totalTurns + 1 } }


newItemNotice : List Model.Item -> Maybe String
newItemNotice items =
    case items of
        [] ->
            Nothing

        _ ->
            Just ("Neue Gegenstände: " ++ String.join ", " (List.map .name items))


offlineMessage : String
offlineMessage =
    "Offline: Du kannst die Geschichte ansehen, aber keine neuen Züge spielen."


scrollToBottom : Cmd Msg
scrollToBottom =
    Dom.getViewportOf "story-feed"
        |> Task.andThen (\info -> Dom.setViewportOf "story-feed" 0 info.scene.height)
        |> Task.attempt ScrolledToBottom
