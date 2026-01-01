module Msg exposing (Msg(..))

type Msg
    = UpdateName String
    | UpdateHouse String
    | ToggleReady
    | ResetState
