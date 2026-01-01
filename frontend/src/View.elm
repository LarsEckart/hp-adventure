module View exposing (view)

import Html exposing (Html, button, div, h1, h2, input, p, span, text)
import Html.Attributes exposing (class, disabled, placeholder, type_, value)
import Html.Events exposing (onClick, onInput)
import Model
import Msg exposing (Msg(..))
import String

view : Model.GameState -> Html Msg
view state =
    div [ class "app" ] <|
        [ headerView
        , errorView state.error
        ]
            ++ viewBody state


headerView : Html Msg
headerView =
    div [ class "header" ]
        [ h1 [] [ text "HP Adventure" ]
        , p [] [ text "Dein interaktives Hogwarts-Abenteuer im Browser." ]
        ]


errorView : Maybe String -> Html Msg
errorView maybeError =
    case maybeError of
        Nothing ->
            text ""

        Just message ->
            div [ class "error" ] [ text message ]


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
            , disabled (not (Model.isProfileComplete state.player))
            ]
            [ text "Abenteuer starten" ]
        ]


startView : Model.GameState -> Html Msg
startView state =
    div [ class "panel" ]
        [ h2 [] [ text "Bereit für Hogwarts?" ]
        , p [] [ text ("Willkommen, " ++ state.player.name ++ " aus " ++ state.player.houseName ++ ".") ]
        , button [ onClick StartAdventure ] [ text "Los geht's" ]
        ]


adventureView : Model.GameState -> Model.Adventure -> Html Msg
adventureView state adventure =
    div [ class "story" ]
        [ div [ class "story-feed" ] (List.map viewTurn adventure.turns)
        , loadingView state.isLoading
        , suggestedActionsView (latestSuggestions adventure)
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
                , disabled (state.isLoading || String.trim state.actionInput == "")
                ]
                [ text "Senden" ]
            ]
        ]


viewTurn : Model.Turn -> Html Msg
viewTurn turn =
    div [ class "turn" ]
        [ userActionView turn.userAction
        , assistantView turn.assistant
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
                [ p [] [ text assistant.storyText ] ]


loadingView : Bool -> Html Msg
loadingView isLoading =
    if isLoading then
        p [ class "loading" ] [ text "Magie sammelt sich..." ]

    else
        text ""


suggestedActionsView : List String -> Html Msg
suggestedActionsView actions =
    if List.isEmpty actions then
        text ""

    else
        div [ class "suggestions" ]
            (List.map suggestedButton actions)


suggestedButton : String -> Html Msg
suggestedButton action =
    button [ onClick (UseSuggestedAction action) ]
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
