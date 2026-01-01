module View exposing (view)

import Html exposing (Html, button, div, h1, input, label, p, text)
import Html.Attributes exposing (class, placeholder, type_, value)
import Html.Events exposing (onClick, onInput)
import Model
import Msg exposing (Msg(..))

view : Model.GameState -> Html Msg
view state =
    div [ class "app" ]
        [ h1 [] [ text "HP Adventure" ]
        , p [] [ text "Placeholder UI: state is saved to localStorage." ]
        , div [ class "field" ]
            [ label [] [ text "Name" ]
            , input
                [ type_ "text"
                , placeholder "Your name"
                , value state.playerName
                , onInput UpdateName
                ]
                []
            ]
        , div [ class "field" ]
            [ label [] [ text "House" ]
            , input
                [ type_ "text"
                , placeholder "House"
                , value state.houseName
                , onInput UpdateHouse
                ]
                []
            ]
        , div [ class "actions" ]
            [ button [ onClick ToggleReady ]
                [ text <|
                    if state.ready then
                        "Ready: yes"

                    else
                        "Ready: no"
                ]
            , button [ onClick ResetState ] [ text "Reset" ]
            ]
        , p [ class "debug" ]
            [ text ("schemaVersion=" ++ String.fromInt state.schemaVersion) ]
        ]
