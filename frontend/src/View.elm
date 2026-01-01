module View exposing (view)

import Html exposing (Html, button, div, h1, h2, h3, img, input, li, p, span, text, ul)
import Html.Attributes exposing (alt, class, disabled, placeholder, src, type_, value)
import Html.Events exposing (onClick, onInput)
import Maybe
import Model
import Msg exposing (Msg(..))
import String

view : Model.GameState -> Html Msg
view state =
    div [ class "app" ] <|
        [ headerView
        , offlineView state.isOnline
        , errorView state.error
        , noticeView state.notice
        ]
            ++ viewBody state


headerView : Html Msg
headerView =
    div [ class "header" ]
        [ h1 [] [ text "HP Adventure" ]
        , p [] [ text "Dein interaktives Hogwarts-Abenteuer im Browser." ]
        ]


offlineView : Bool -> Html Msg
offlineView isOnline =
    if isOnline then
        text ""

    else
        div [ class "offline-banner" ]
            [ p [] [ text "Offline: Du kannst die Geschichte ansehen, aber keine neuen Züge spielen." ] ]


errorView : Maybe String -> Html Msg
errorView maybeError =
    case maybeError of
        Nothing ->
            text ""

        Just message ->
            div [ class "error" ] [ text message ]


noticeView : Maybe String -> Html Msg
noticeView maybeNotice =
    case maybeNotice of
        Nothing ->
            text ""

        Just message ->
            div [ class "notice" ]
                [ p [] [ text message ]
                , button [ class "notice-close", onClick DismissNotice ] [ text "Ok" ]
                ]


viewBody : Model.GameState -> List (Html Msg)
viewBody state =
    if not (Model.isProfileComplete state.player) then
        [ setupView state ]

    else
        case state.currentAdventure of
            Nothing ->
                [ startView state ]

            Just adventure ->
                [ adventureView state adventure ]


setupView : Model.GameState -> Html Msg
setupView state =
    div [ class "panel" ]
        [ h2 [] [ text "Wer bist du?" ]
        , div [ class "field" ]
            [ span [] [ text "Name" ]
            , input
                [ type_ "text"
                , placeholder "Dein Name"
                , value state.player.name
                , onInput UpdateName
                ]
                []
            ]
        , div [ class "field" ]
            [ span [] [ text "Haus" ]
            , input
                [ type_ "text"
                , placeholder "Gryffindor, Ravenclaw..."
                , value state.player.houseName
                , onInput UpdateHouse
                ]
                []
            ]
        , button
            [ onClick StartAdventure
            , disabled (not (Model.isProfileComplete state.player) || not state.isOnline)
            ]
            [ text "Abenteuer starten" ]
        ]


startView : Model.GameState -> Html Msg
startView state =
    div [ class "start-layout" ]
        [ div [ class "panel" ]
            [ h2 [] [ text "Bereit für Hogwarts?" ]
            , p [] [ text ("Willkommen, " ++ state.player.name ++ " aus " ++ state.player.houseName ++ ".") ]
            , button [ onClick StartAdventure, disabled (not state.isOnline) ] [ text "Los geht's" ]
            ]
        , div [ class "meta-grid" ]
            [ statsPanel state.player
            , inventoryPanel state.player state.showInventory
            , historyPanel state.player state.showHistory
            ]
        ]


adventureView : Model.GameState -> Model.Adventure -> Html Msg
adventureView state adventure =
    div [ class "story-layout" ]
        [ div [ class "story-main" ]
            [ div [ class "story" ]
                [ div [ class "story-feed" ] (List.map viewTurn adventure.turns)
                , loadingView state.isLoading
                , suggestedActionsView state.isLoading state.pendingAbandon state.isOnline (latestSuggestions adventure)
                , abandonConfirmView state.pendingAbandon
                , div [ class "action-bar" ]
                    [ input
                        [ type_ "text"
                        , placeholder "Was tust du?"
                        , value state.actionInput
                        , onInput UpdateActionInput
                        ]
                        []
                    , button
                        [ onClick SendAction
                        , disabled (state.isLoading || state.pendingAbandon || String.trim state.actionInput == "" || not state.isOnline)
                        ]
                        [ text "Senden" ]
                    , button
                        [ class "ghost"
                        , onClick RequestAbandon
                        , disabled state.isLoading
                        ]
                        [ text "Aufgeben" ]
                    ]
                ]
            ]
        , div [ class "story-sidebar" ]
            [ statsPanel state.player
            , currentAdventurePanel adventure
            , inventoryPanel state.player state.showInventory
            , historyPanel state.player state.showHistory
            ]
        ]


viewTurn : Model.Turn -> Html Msg
viewTurn turn =
    div [ class "turn" ]
        [ div [ class "turn-main" ]
            [ userActionView turn.userAction
            , assistantView turn.assistant
            ]
        , div [ class "turn-image" ]
            [ assistantImageView turn.assistant ]
        ]


userActionView : String -> Html Msg
userActionView action =
    p [ class "user-action" ]
        [ text ("Du: " ++ action) ]


assistantView : Maybe Model.AssistantTurn -> Html Msg
assistantView maybeAssistant =
    case maybeAssistant of
        Nothing ->
            div [ class "assistant pending" ] [ text "Die Geschichte schreibt sich..." ]

        Just assistant ->
            div [ class "assistant" ]
                [ p [] [ text assistant.storyText ]
                , newItemsView assistant.newItems
                , completionView assistant.adventureCompleted
                ]


assistantImageView : Maybe Model.AssistantTurn -> Html Msg
assistantImageView maybeAssistant =
    case maybeAssistant of
        Nothing ->
            div [ class "assistant-image pending" ] [ text "Illustration lädt..." ]

        Just assistant ->
            case assistant.image of
                Nothing ->
                    div [ class "assistant-image pending" ] [ text "Illustration wird vorbereitet..." ]

                Just imageData ->
                    let
                        imageSrc =
                            "data:" ++ imageData.mimeType ++ ";base64," ++ imageData.base64

                        description =
                            Maybe.withDefault "Illustration der Szene" imageData.prompt
                    in
                    img [ class "assistant-image", src imageSrc, alt description ] []


loadingView : Bool -> Html Msg
loadingView isLoading =
    if isLoading then
        p [ class "loading" ] [ text "Magie sammelt sich..." ]

    else
        text ""


suggestedActionsView : Bool -> Bool -> Bool -> List String -> Html Msg
suggestedActionsView isLoading isAbandoning isOnline actions =
    if List.isEmpty actions then
        text ""

    else
        div [ class "suggestions" ]
            (List.map (suggestedButton isLoading isAbandoning isOnline) actions)


suggestedButton : Bool -> Bool -> Bool -> String -> Html Msg
suggestedButton isLoading isAbandoning isOnline action =
    button
        [ onClick (UseSuggestedAction action)
        , disabled (isLoading || isAbandoning || not isOnline)
        ]
        [ text action ]


latestSuggestions : Model.Adventure -> List String
latestSuggestions adventure =
    case List.reverse adventure.turns of
        [] ->
            []

        lastTurn :: _ ->
            case lastTurn.assistant of
                Nothing ->
                    []

                Just assistant ->
                    assistant.suggestedActions


newItemsView : List Model.Item -> Html Msg
newItemsView items =
    case items of
        [] ->
            text ""

        _ ->
            div [ class "new-items" ]
                [ p [] [ text "Neue Gegenstände:" ]
                , ul []
                    (List.map
                        (\item -> li [] [ text (item.name ++ " — " ++ item.description) ])
                        items
                    )
                ]


completionView : Bool -> Html Msg
completionView completed =
    if completed then
        p [ class "completion" ] [ text "Abenteuer abgeschlossen!" ]

    else
        text ""


abandonConfirmView : Bool -> Html Msg
abandonConfirmView isVisible =
    if isVisible then
        div [ class "confirm" ]
            [ p [] [ text "Willst du das Abenteuer wirklich aufgeben?" ]
            , div [ class "confirm-actions" ]
                [ button [ onClick ConfirmAbandon ] [ text "Ja, aufgeben" ]
                , button [ class "ghost", onClick CancelAbandon ] [ text "Abbrechen" ]
                ]
            ]

    else
        text ""


statsPanel : Model.Player -> Html Msg
statsPanel player =
    div [ class "panel panel-side" ]
        [ h3 [] [ text "Statistik" ]
        , div [ class "stats-grid" ]
            [ statItem "Abenteuer" (String.fromInt player.stats.adventuresCompleted)
            , statItem "Züge" (String.fromInt player.stats.totalTurns)
            ]
        ]


statItem : String -> String -> Html Msg
statItem label valueText =
    div [ class "stat-item" ]
        [ span [] [ text label ]
        , p [] [ text valueText ]
        ]


currentAdventurePanel : Model.Adventure -> Html Msg
currentAdventurePanel adventure =
    let
        titleText =
            case adventure.title of
                Just title ->
                    title

                Nothing ->
                    "Unbenanntes Abenteuer"
    in
    div [ class "panel panel-side" ]
        [ h3 [] [ text "Aktuelles Abenteuer" ]
        , p [] [ text titleText ]
        ]


inventoryPanel : Model.Player -> Bool -> Html Msg
inventoryPanel player isVisible =
    div [ class "panel panel-side" ]
        [ panelHeader "Inventar" ToggleInventory isVisible
        , if isVisible then
            if List.isEmpty player.inventory then
                p [] [ text "Noch keine Gegenstände." ]

            else
                ul [ class "panel-list" ]
                    (List.map
                        (\item -> li [] [ text (item.name ++ " — " ++ item.description) ])
                        player.inventory
                    )

          else
            text ""
        ]


historyPanel : Model.Player -> Bool -> Html Msg
historyPanel player isVisible =
    div [ class "panel panel-side" ]
        [ panelHeader "Abenteuerchronik" ToggleHistory isVisible
        , if isVisible then
            if List.isEmpty player.completedAdventures then
                p [] [ text "Noch keine abgeschlossenen Abenteuer." ]

            else
                ul [ class "panel-list" ]
                    (List.map historyItemView player.completedAdventures)

          else
            text ""
        ]


historyItemView : Model.CompletedAdventure -> Html Msg
historyItemView adventure =
    li [ class "history-item" ]
        [ p [] [ text adventure.title ]
        , p [ class "muted" ] [ text adventure.summary ]
        , p [ class "muted" ] [ text adventure.completedAt ]
        ]


panelHeader : String -> Msg -> Bool -> Html Msg
panelHeader title toggleMsg isOpen =
    div [ class "panel-header" ]
        [ h3 [] [ text title ]
        , button [ class "ghost", onClick toggleMsg ]
            [ text (if isOpen then "Ausblenden" else "Anzeigen") ]
        ]
