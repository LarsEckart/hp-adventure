module Update exposing (update)

import Model
import Msg exposing (Msg(..))

update : (Model.GameState -> Cmd Msg) -> Msg -> Model.GameState -> ( Model.GameState, Cmd Msg )
update save msg state =
    case msg of
        UpdateName name ->
            let
                next =
                    { state | playerName = name }
            in
            ( next, save next )

        UpdateHouse house ->
            let
                next =
                    { state | houseName = house }
            in
            ( next, save next )

        ToggleReady ->
            let
                next =
                    { state | ready = not state.ready }
            in
            ( next, save next )

        ResetState ->
            let
                next =
                    Model.defaultState
            in
            ( next, save next )
