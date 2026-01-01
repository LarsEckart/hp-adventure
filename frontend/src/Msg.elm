module Msg exposing (Msg(..))

import Api

type Msg
    = UpdateName String
    | UpdateHouse String
    | UpdateActionInput String
    | OnlineStatusChanged Bool
    | StartAdventure
    | SendAction
    | UseSuggestedAction String
    | GotStoryResponse (Result Api.HttpError Api.StoryResponse)
    | ToggleInventory
    | ToggleHistory
    | RequestAbandon
    | ConfirmAbandon
    | CancelAbandon
    | DismissNotice
    | ResetState
